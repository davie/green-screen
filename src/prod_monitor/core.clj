(ns prod-monitor.core
  (:use clojure.test)
  (:use noir.core)
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:require [clj-http.client :as client])
  (:require [noir.server :as server])
  (:import java.util.concurrent.Executors)
  (:import java.util.concurrent.TimeUnit)
  (:gen-class :main true))

;; :untested, :pass, :fail
(def overall-status (atom {:state :untested}))

(defrecord SystemState [name url status result])
(def individual-results (atom {})) ; {:name system-state}

(def colours {:untested "yellow"
              :pass "green"
              :fail "red"})

(def systems-to-check
  (atom {:local "http://127.0.0.1" 
         :bad "http://127.0.0.1/blah"
         :another "http://127.0.0.1/blah"}) )

;;(def systems-to-check
;;  {:local "http://127.0.0.1" (http-status 200)
;;   :bad "http://127.0.0.1/blah" (json-with (= "pass" (-> % :a :b first)))
;;   :another "http://127.0.0.1/blah"} (http-status 200))

;;(def systems-to-check {:local "http://localhost"})

(defn check-url [[system-name url]]
  (let [response (client/get url {:throw-exceptions false :timout 1000})
        success (if (= 200 (-> response :status)) :pass :fail)]
    (SystemState. system-name, url, success, response)))

(defn set-systems! [systems]
  (reset! systems-to-check  systems)
  (reset! individual-results {})
  (reset! overall-status {}))

(defn check-status []
  (let [statuses (pmap check-url @systems-to-check)
        stats-map (into {} (map (juxt :name identity) statuses))]
    (swap! individual-results conj stats-map)
    
    (let [{failures :fail, successes :pass :as all} (group-by :status (vals @individual-results)) 
          state (when (seq all)
                  (if (seq failures) :fail :pass))]
      (swap! overall-status conj {:state state})
      (println @overall-status)
      all)))

(defn start []
  ;; all at once, think about how we want to do this.
  ;; is a pmap all that bad?
  (.scheduleAtFixedRate (Executors/newScheduledThreadPool 1) check-status 0 10 TimeUnit/SECONDS))

(defn start-web []
  (server/start 8080))

(defn -main []
  (println "hi")
  (start-web)
  (start))

(defn force-to-pass []
  (swap! overall-status conj {:state :pass}))

;; WEB
;; Move this out shortly
(defpartial system-item
    [[system-name {res :result url :url success :status}]]
  [:li {:class (str (name success) " result")} (str system-name " url: " url " passed? " success)])

(defpage "/" []
  (let [content
        (-> @overall-status :state colours)]
    (html
     (html5
      [:head
        [:title (str "All systems are " (name content))]
       ;; [:meta {:http-equiv "refresh", :content "5"}]
       (include-css "checker.css")]
      [:body {:class content :style (str "background-color:" content)}
       [:h1#title (name content)]
       [:ul (map system-item @individual-results)]]))))


;; TODO
;; package as an executable jar
;; handle json with success/fail embedded - maybe have a predicate
;; variable timeouts
;; shell commands
;; logging
;; report the failure message
;; make the web page pretty
;; push to web page?
;; think about transactions - now have two uncoordinated atoms
;; test
;; sort out scheduling in the repl


;; reading
;; http://www.ewernli.com/clojure-agents
;; http://justin.harmonize.fm/index.php/2009/03/clojure-agents/
