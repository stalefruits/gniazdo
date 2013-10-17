(ns gniazdo.core
  (:import java.net.URI
           (org.eclipse.jetty.websocket.client ClientUpgradeRequest
                                               WebSocketClient)
           (org.eclipse.jetty.websocket.api WebSocketListener
                                            Session)))

(set! *warn-on-reflection* 1)

(defprotocol ^:private Client
  (send-msg [this ^String msg] "Sends a message to the given WebSocket.")
  (close [this] "Closes the WebSocket."))

(defn- noop
  [& _])

(defn connect
  "Connects to a WebSocket at a given URI (e.g. ws://example.org:1234/socket)."
  [uri & {:keys [on-connect on-receive on-error on-close]
          :or {on-connect noop
               on-receive noop
               on-error   noop
               on-close   noop}}]
  (let [request (ClientUpgradeRequest.)
        client (WebSocketClient.)
        result-promise (promise)
        listener (reify WebSocketListener
                   (onWebSocketText [_ msg]
                     (on-receive msg))
                   (onWebSocketError [_ throwable]
                     (if (realized? result-promise)
                       (on-error throwable)
                       (deliver result-promise throwable)))
                   (onWebSocketConnect [_ session]
                     (deliver result-promise session)
                     (on-connect session))
                   (onWebSocketClose [_ x y]
                     (on-close x y)))]
    (try
      (.start client)
      (.connect client listener (URI. uri) request)
      (let [result @result-promise
            ^Session session (if (instance? Throwable result)
                               (throw result)
                               result)]
        (reify Client
          (send-msg [_ msg] (-> session
                                .getRemote
                                (.sendString msg)))
          (close [_] (do
                       (.close session)
                       (.stop client)))))
      (catch Throwable ex
        (.stop client)
        (throw (Exception. ex))))))
