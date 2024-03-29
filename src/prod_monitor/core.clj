( ns prod-monitor.core
  (:use clojure.test)
  (:use noir.core)
  (:use hiccup.core)
  (:use hiccup.page-helpers)  
  (:require [clj-http.client :as client])
  (:require [noir.server :as server])
  (:require [cheshire.core :as cheshire])
  (:require [clj-ssh.ssh :as ssh])
  (:import java.util.concurrent.Executors)
  (:import java.util.concurrent.TimeUnit)
  (:gen-class :main true)
  (:use [timbre.core :as timbre :only (trace debug info warn error fatal spy)])
  (:use [clojure.string :only (trim)]))

;; :untested, :pass, :fail
(def overall-status (atom {:state :untested}))

(defrecord Status [success, description])
(defrecord SystemState [name url status result])
(def individual-results (atom {})) ; {:name system-state}

(def colours {:untested "yellow"
              :pass "green"
              :fail "red"})

(defn http-status [success-status]
  (fn [status content] (Status. (if (= status success-status) :pass :fail)
                                (str "expect http status:" success-status "got: " status))))

(defn stdout-matches [pred]
  (fn [status content]
    (Status. (if (pred (trim content)) :pass :fail) content)))

(defn json-value-matches [k v]
  (info "k " k "v " v)
  (fn [status content] (let [content-map (cheshire/parse-string content)
                             val (get content-map k)]
                         (info "content: " content " val: " val)
                         (Status. (if (= (get content-map k) v) :pass :fail) (str "content: " val " expected: " v)))))

(def systems-to-check
  (atom {:local {:type :http :url "http://127.0.0.1" :success-fn (http-status 200)} 
         :bad {:type :http :url  "http://127.0.0.1/blah" :success-fn (http-status 200)} 
         :happy-ssh {:type :ssh :command "echo 'hi'" :host "localhost" :success-fn (stdout-matches #(= "hi" (trim %)))}
         :fail-ssh {:type :ssh :command "lss" :host "localhost" }
         }))

(defmulti check (fn [[_ m]] (:type m)))

(defmethod check :http [[system-name {url :url success-fn :success-fn} ]]
  (let [response (client/get url {:throw-exceptions false :timout 1000})
        pred (or success-fn (http-status 200))
        success (pred (:status response) (:body response) )
        ]
    (info "system name " system-name "fn " success-fn)
    (info "response: \"" response "\" success: \"" success "\"")
    (SystemState. system-name, url, success, response)))

(defmethod check :ssh [[system-name {command :command host :host success-fn :success-fn} ]]
  (ssh/default-session-options {:strict-host-key-checking :no})
  (let [response (ssh/ssh host command)
        pred (or success-fn (fn [_ _] true))
        [return-code std-out] response
        success (if (and (= 0 return-code) (pred return-code std-out)) :pass :fail)]
    (info "system name " system-name "fn " success-fn)
    (info "response: \"" response "\" success: \"" success "\"")
    (SystemState. system-name, (str host ":" command), (Status. success ""), response)))

(defn set-systems! [systems]
  (reset! systems-to-check systems)
  (reset! individual-results {})
  (reset! overall-status {}))

(defn check-status []
  (let [statuses (pmap check @systems-to-check)
        stats-map (into {} (map (juxt :name identity) statuses))]
    (swap! individual-results conj stats-map)
    
    (let [{failures :fail, successes :pass :as all} (group-by #(-> % :status :success) (vals @individual-results)) 
          state (when (seq all)
                  (if (seq failures) :fail :pass))]
      (swap! overall-status conj {:state state})
      (info @overall-status)
      all)))

(defn start []
  ;; all at once, think about how we want to do this.
  ;; is a pmap all that bad?
  (.scheduleAtFixedRate (Executors/newScheduledThreadPool 1) check-status 0 10 TimeUnit/SECONDS))

(defn start-web []
  (server/start 8080))

(defn -main []
  (info "starting")
  (start-web)
  (start))

(defn force-to-pass []
  (swap! overall-status conj {:state :pass}))

;; WEB
;; Move this out shortly
(defpartial system-item
    [[system-name {res  :result url :url { success :success description :description} :status}]]
  [:li {:class (str (name success) " result")} (str system-name " url: " url " passed? " success "description" description)])

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
;; Report the failure message
;; Allow config separate from the app
;; Variable timeouts
;; Configurable poll frequency
;; Logging
;; Make the web page pretty
;; Push to web page?
;; Think about transactions - now have two uncoordinated atoms
;; Sort out scheduling in the repl
;;
;; DONE
;; package as an executable jar - uberjar
;; test 
;; handle json with success/fail embedded - maybe have a predicate
;; Shell commands

;; reading
;; http://www.ewernli.com/clojure-agents
;; http://justin.harmonize.fm/index.php/2009/03/clojure-agents/
;; http://info.rjmetrics.com/blog/bid/54114/Parallel-SSH-and-system-monitoring-in-Clojure
;; https://github.com/hugoduncan/clj-ssh
