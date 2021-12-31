(ns gniazdo.core
  (:import java.net.URI
           java.nio.ByteBuffer
           java.util.List
           (org.eclipse.jetty.websocket.client ClientUpgradeRequest
                                               WebSocketClient)
           (org.eclipse.jetty.util.ssl SslContextFactory$Client)
           (org.eclipse.jetty.websocket.api WebSocketListener
                                            RemoteEndpoint
                                            Session
                                            ExtensionConfig)))

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
  (close [this] [this status-code reason]
    "Closes the WebSocket."))

;; ## WebSocket Helpers

(defn- add-headers!
  [^ClientUpgradeRequest request headers]
  {:pre [(every? string? (keys headers))]}
  (doseq [[header value] headers]
    (let [header-values (if (sequential? value)
                          value
                          [value])]
      (assert (every? string? header-values))
      (.setHeader
        request
        ^String header
        ^java.util.List header-values))))

(defn- add-subprotocols!
  [^ClientUpgradeRequest request subprotocols]
  {:pre [(or (nil? subprotocols) (sequential? subprotocols))
         (every? string? subprotocols)]}
  (when (seq subprotocols)
    (.setSubProtocols request ^List (into () subprotocols))))

(defn- add-extensions!
  [^ClientUpgradeRequest request extensions]
  {:pre [(or (nil? extensions) (sequential? extensions))
         (every? string? extensions)]}
  (when (seq extensions)
    (.setExtensions request ^List (map #(ExtensionConfig/parse ^String %)
                                       extensions))))

(defn- upgrade-request
  ^ClientUpgradeRequest
  [{:keys [headers subprotocols extensions]}]
  (doto (ClientUpgradeRequest.)
    (add-headers! headers)
    (add-subprotocols! subprotocols)
    (add-extensions! extensions)))

(defn- listener
  ^WebSocketListener
  [{:keys [on-connect on-receive on-binary on-error on-close]
    :or {on-connect (constantly nil)
         on-receive (constantly nil)
         on-binary  (constantly nil)
         on-error   (constantly nil)
         on-close   (constantly nil)}}
   result-promise]
  (reify WebSocketListener
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
      (on-close x y))))

(defn- deref-session
  ^Session
  [result-promise]
  (let [result @result-promise]
    (if (instance? Throwable result)
      (throw result)
      result)))

;; ## WebSocket Client + Connection (API)

(defn client
  "Create a new instance of `WebSocketClient`. If the optionally supplied URI
   is representing a secure WebSocket endpoint (\"wss://...\") an SSL-capable
   instance will be returned."
  (^WebSocketClient
    [] (WebSocketClient.))
  (^WebSocketClient
    [^URI uri]
    (if (= "wss" (.getScheme uri))
      (WebSocketClient. (SslContextFactory$Client.))
      (WebSocketClient.))))

(defn- connect-with-client
  "Connect to a WebSocket using the supplied `WebSocketClient` instance."
  [^WebSocketClient client ^URI uri opts]
   (let [request (upgrade-request opts)
         cleanup (::cleanup opts)
         result-promise (promise)
         listener (listener opts result-promise)]
     (.connect client listener uri request)
     (let [session (deref-session result-promise)]
       (reify Client
         (send-msg [_ msg]
           (send-to-endpoint msg (.getRemote session)))
         (close [_]
           (.close session)
           (when cleanup
             (cleanup)))
         (close [_ status-code reason]
           (.close session status-code reason)
           (when cleanup
             (cleanup)))))))

(defn- connect-helper
  [^URI uri opts]
  (let [client (client uri)]
    (try
      (.start client)
      (->> (assoc opts ::cleanup #(.stop client))
           (connect-with-client client uri))
      (catch Throwable ex
        (.stop client)
        (throw ex)))))

(defn connect
  "Connects to a WebSocket at a given URI (e.g. ws://example.org:1234/socket)."
  {:style/indent 1}
  [uri & {:keys [on-connect on-receive on-binary on-error on-close headers client
                 subprotocols extensions]
          :as opts}]
  (let [uri' (URI. uri)]
    (if client
      (connect-with-client client uri' opts)
      (connect-helper uri' opts))))
