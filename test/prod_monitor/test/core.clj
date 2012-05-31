(ns prod-monitor.test.core
  (:use [prod-monitor.core])
  (:require [noir.server :as noir])
  (:use [clojure.test]))

(defn dummy-server [name result]
  (ring.adapter.jetty/run-jetty
   (fn [a]  {:status result :body "hi" })
   {:port 0 :join? false}))

(defn check
  ([name result]
     (check name result ""))
  ([name result content]
     ( let [server (dummy-server name result)
            port (-> server .getConnectors first .getLocalPort)]
       (set-systems! {name {:url (str "http://localhost:" port)}})
       (println @systems-to-check)
       (check-status))))

(deftest defaults-to-untested
  (set-systems! {:not-tested {:url "http://anything"}})
  (check-status)
  (is (= [:not-tested] (map :name (vals @individual-results))))
  (is (= {:state :fail} @overall-status)))

;; hard-coded to failing urls, stub them out
(deftest goes-red
  (set-systems! {})
  (check :red 500)
  (is (= [:red] (map :name (vals @individual-results))))
  (is (= {:state :fail} @overall-status)))

;; this gets appended to the overall state...
(deftest green
  (check :green-system 200 )
  (is (= [:green-system] (map :name (vals @individual-results))))
  (is (= {:state :pass} @overall-status)))

(deftest json-status-check
  (check :green-json 200 "{\"status\" : \"success\", \"message\" : \"all is well\"}")
  (is (= [:green-json] (map :name (vals @individual-results))))
  (is (= {:state :pass} @overall-status)))

(comment
  (deftest json-status-check-red
   (check :red-json 200 "{\"status\" : \"fail\", \"message\" : \"its hit the fan\"}")
   (is (= [:red-json] (map :name (vals @individual-results))))
   (is (= {:state :fail} @overall-status)))
 

 {
  :key :success
  :criteria (partial = "hi")
  :fail-description :message
  }

 (defn map-matcher [m path success-value]
   (= (path m) "success-value")))