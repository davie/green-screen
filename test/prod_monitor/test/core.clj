(ns prod-monitor.test.core
  (:use [prod-monitor.core])
  (:require [noir.server :as noir])
  (:use [clojure.test]))

(defn dummy-server [name result content]
  (ring.adapter.jetty/run-jetty
   (fn [a]  {:status result :body content })
   {:port 0 :join? false}))

(defn do-check
  ([name result]
     (do-check name result "" nil))
  ([name result content pred]
     ( let [server (dummy-server name result content)
            port (-> server .getConnectors first .getLocalPort)]
       (set-systems! {name {:type :http :url (str "http://localhost:" port) :success-fn pred}})
       (println @systems-to-check)
       (check-status))))

(deftest defaults-to-untested
  (set-systems! {:not-tested {:type :http :url "http://anything"}})
  (check-status)
  (is (= [:not-tested] (map :name (vals @individual-results))))
  (is (= {:state :fail} @overall-status)))

;; hard-coded to failing urls, stub them out
(deftest goes-red
  (set-systems! {})
  (do-check :red 500)
  (is (= [:red] (map :name (vals @individual-results))))
  (is (= {:state :fail} @overall-status)))

;; this gets appended to the overall state...
(deftest green
  (do-check :green-system 200 )
  (is (= [:green-system] (map :name (vals @individual-results))))
  (is (= {:state :pass} @overall-status)))

(deftest json-status-check
  (do-check
   :green-json 200
   "{\"status\" : \"success\", \"message\" : \"all is well\"}"
   (json-value-matches "status" "success"))
  (is (= [:green-json] (map :name (vals @individual-results))))
  (is (= {:state :pass} @overall-status)))


(deftest json-status-check-red
  (do-check :red-json 200
         "{\"status\" : \"fail\", \"message\" : \"its hit the fan\"}"
         (json-value-matches "status" "success"))
  (is (= [:red-json] (map :name (vals @individual-results))))
  (is (= (-> @individual-results :red-json :status :description) "content: fail expected: success"))
  (is (= {:state :fail} @overall-status)))

;; also need to test failing to parse = error
(comment
  ; maybe something like this?
  {
   :key :success
   :criteria (partial = "hi")
   :fail-description :message
   }
)