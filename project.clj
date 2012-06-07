(defproject prod-monitor "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [clj-http "0.2.7"]
                 [ring/ring-core "1.0.2"]
                 [ring/ring-jetty-adapter "1.0.2"]
                 [ring/ring-devel "1.0.2"]
                 [ring-reload-modified "0.1.1"]
                 [hiccup "0.3.8"]
                 [noir "1.2.2" :exclusions [org.clojure/clojure]]
                 [cheshire "4.0.0"]
                 [clj-ssh "0.3.2"]
                 ]
  :dev-dependencies [[lein-run "1.0.1-SNAPSHOT" ]]
  :main prod-monitor.core
  )
