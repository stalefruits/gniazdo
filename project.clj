(defproject stylefruits/gniazdo "1.2.1"
  :description "A WebSocket client for Clojure"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"
            :year 2013
            :key "apache-2.0"
            :author "stylefruits GmbH"}
  :url "https://github.com/stylefruits/gniazdo"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.eclipse.jetty.websocket/websocket-client "9.4.46.v20220331"]]
  :repl-options {:init-ns gniazdo.core}
  :jvm-opts ["-Dorg.eclipse.jetty.websocket.client.LEVEL=WARN"]
  :profiles {:dev
             {:dependencies [[http-kit "2.5.3"]]}})
