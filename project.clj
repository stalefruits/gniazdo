(defproject stylefruits/gniazdo "0.1.1-SNAPSHOT"
  :description "A WebSocket client for Clojure"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.eclipse.jetty.websocket/websocket-client "9.1.0.RC1"]]
  :repl-options {:init-ns gniazdo.core}
  :jvm-opts ["-Dorg.eclipse.jetty.websocket.client.LEVEL=WARN"]
  :profiles {:dev
             {:dependencies [[http-kit "2.1.13"]]}})
