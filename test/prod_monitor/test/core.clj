(ns prod-monitor.test.core
  (:use [prod-monitor.core])
  (:require [noir.server :as noir])
  (:use [clojure.test]))

(defn dummy-server [name result]
  (ring.adapter.jetty/run-jetty
   (fn [a]  {:status result :body "hi" })
   {:port 0 :join? false}))



(defn check [name result]
  (let [server (dummy-server name result)
        port (-> server .getConnectors first .getLocalPort)]
    (set-systems! {name (str "http://localhost:" port)})
    (println @systems-to-check)
    (check-status)))

(deftest defaults-to-untested
  (set-systems! {:not-tested "http://anything"})
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





