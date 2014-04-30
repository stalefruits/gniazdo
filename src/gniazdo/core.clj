(ns gniazdo.core
  (:import java.net.URI
           java.nio.ByteBuffer
           (org.eclipse.jetty.websocket.client ClientUpgradeRequest
                                               WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory)
           (org.eclipse.jetty.websocket.api WebSocketListener
                                            RemoteEndpoint
                                            Session)))

(set! *warn-on-reflection* 1)

;; ## Messages

(defprotocol Sendable
  (send-to-endpoint [this ^RemoteEndpoint e]
    "Sends an entity to a given WebSocket endpoint."))

(extend-protocol Sendable
  java.lang.String
  (send-to-endpoint [msg ^RemoteEndpoint e]
    (.sendString e msg))

  java.nio.ByteBuffer
  (send-to-endpoint [buf ^RemoteEndpoint e]
    (.sendBytes e buf)))

(extend-type (class (byte-array 0))
  Sendable
  (send-to-endpoint [data ^RemoteEndpoint e]
    (.sendBytes e (ByteBuffer/wrap data))))

;; ## Client

(defprotocol ^:private Client
  (send-msg [this msg]
    "Sends a message (implementing `gniazdo.core/Sendable`) to the given WebSocket.")
  (close [this]
    "Closes the WebSocket."))

(defn- noop
  [& _])

(defn- add-headers!
  [^ClientUpgradeRequest request headers]
  {:pre (every? string? (keys headers))}
  (doseq [[header value] headers]
    (let [header-values (if (sequential? value)
                          value
                          [value])]
      (assert (every? string? header-values))
      (.setHeader
        request
        ^String header
        ^java.util.List header-values))))

(defn connect
  "Connects to a WebSocket at a given URI (e.g. ws://example.org:1234/socket)."
  [uri & {:keys [on-connect on-receive on-binary on-error on-close headers]
          :or {on-connect noop
               on-receive noop
               on-binary  noop
               on-error   noop
               on-close   noop}}]
  (let [request (doto (ClientUpgradeRequest.)
                  (add-headers! headers))
        uri (URI. uri)
        client (if (= "wss" (.getScheme uri))
                 (WebSocketClient. (SslContextFactory.))
                 (WebSocketClient.))
        result-promise (promise)
        listener (reify WebSocketListener
                   (onWebSocketText [_ msg]
                     (on-receive msg))
                   (onWebSocketBinary [_ data offset length]
                     (on-binary data offset length))
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
      (.connect client listener uri request)
      (let [result @result-promise
            ^Session session (if (instance? Throwable result)
                               (throw result)
                               result)]
        (reify Client
          (send-msg [_ msg]
            (send-to-endpoint msg (.getRemote session)))
          (close [_] (do
                       (.close session)
                       (.stop client)))))
      (catch Throwable ex
        (.stop client)
        (throw (Exception. ex))))))
