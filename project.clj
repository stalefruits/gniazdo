(defproject stylefruits/gniazdo "1.2.0"
  :description "A WebSocket client for Clojure"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"
            :year 2013
            :key "apache-2.0"
            :author "stylefruits GmbH"}
  :url "https://github.com/stylefruits/gniazdo"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.eclipse.jetty.websocket/websocket-jetty-api "11.0.7"]
                 [org.eclipse.jetty.websocket/websocket-jetty-client "11.0.7"]]
  :repl-options {:init-ns gniazdo.core}
  :javac-options ["-target" "11" "-source" "11"]
  :jvm-opts ["-Dorg.eclipse.jetty.websocket.client.LEVEL=WARN"]
  :profiles {:dev
             {:dependencies [[http-kit "2.5.3"]]}})
