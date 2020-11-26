(ns gniazdo.core-test
  (:require [clojure.string :as str])
  (:use clojure.test
        gniazdo.core
        [org.httpkit.server :only [with-channel
                                   on-receive
                                   on-close
                                   run-server
                                   send!]])
  (:import [java.util.concurrent Future]
           [org.eclipse.jetty.websocket.api Session]))

(declare ^:dynamic *recv*)
(def close-code (atom nil))

(defn- ws-srv
  [req]
  (with-channel req conn
    (on-receive conn (partial *recv* req conn))
    (on-close conn #(reset! close-code %))))

(use-fixtures
  :each
  (fn [f]
    (reset! close-code nil)
    (let [srv (run-server ws-srv {:port 65432})]
      (try
        (f)
        (finally
          (srv))))))

(def ^:private uri "ws://localhost:65432/")

(defmacro ^:private with-timeout
  [& body]
  `(let [f# (future ~@body)]
     (try
       (.get ^Future f# 1 java.util.concurrent.TimeUnit/SECONDS)
       (finally
         (future-cancel f#)))))

(deftest on-receive-test
  (with-redefs [*recv* (fn [_ conn msg]
                         (send! conn (str/upper-case msg)))]
    (let [result (atom nil)
          sem (java.util.concurrent.Semaphore. 0)
          conn (connect
                 uri
                 :on-receive #(do (reset! result %)
                                  (.release sem)))]
      (is (= @result nil))
      (send-msg conn "foo")
      (with-timeout (.acquire sem))
      (is (= @result "FOO"))
      (send-msg conn "bar")
      (with-timeout (.acquire sem))
      (is (= @result "BAR"))
      (close conn))))

(deftest on-binary-test
  (with-redefs [*recv* (fn [_ conn msg]
                         (send! conn (if (string? msg)
                                       (.getBytes (str/upper-case msg))
                                       msg)))]
    (let [result (atom nil)
          sem (java.util.concurrent.Semaphore. 0)
          conn (connect
                 uri
                 :on-binary (fn [data offset length]
                              (reset! result (String. data offset length))
                              (.release sem)))]
      (is (= @result nil))
      (send-msg conn "foo")
      (with-timeout (.acquire sem))
      (is (= @result "FOO"))
      (send-msg conn "bar")
      (with-timeout (.acquire sem))
      (is (= @result "BAR"))
      (send-msg conn (.getBytes "bar"))
      (with-timeout (.acquire sem))
      (is (= @result "bar"))
      (close conn))))

(deftest on-connect-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :on-connect (fn [_]
                             (reset! result :connected)
                             (.release sem)))]
    (with-timeout (.acquire sem))
    (is (= @result :connected))
    (close conn)))

(deftest on-close-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :on-close (fn [& _]
                           (reset! result :closed)
                           (.release sem)))]
    (is (= @result nil))
    (close conn)
    (with-timeout (.acquire sem))
    (is (= @result :closed))))

(deftest close-with-code-test
  (let [sem (java.util.concurrent.Semaphore. 0)
        conn (connect
              uri
              :on-close (fn [& _]
                          (.release sem)))]
    (is (= @close-code nil))
    (close conn 1003 "Unsupported")
    (with-timeout (.acquire sem))
    (is (= @close-code :unsupported))))

(deftest on-error-test
  (testing "invalid arity"
    (testing ":on-connect"
      (let [result (promise)
            conn (connect
                   uri
                   :on-error (fn on-error [ex] (deliver result ex))
                   :on-connect (fn on-connect [_ _ _ _ _]))]
        (is (instance? clojure.lang.ArityException
                       (with-timeout @result)))
        (close conn)))
    (testing ":on-receive"
      (with-redefs [*recv* (fn [_ conn msg] (send! conn ""))]
        (let [result (promise)
              conn (connect
                     uri
                     :on-error (fn on-error [ex] (deliver result ex))
                     :on-receive (fn on-receive [_ _ _ _ _]))]
          (send-msg conn "")
          (is (instance? clojure.lang.ArityException
                         (with-timeout @result)))
          (close conn))))))

(deftest subprotocols-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
              uri
              :subprotocols ["wamp"]
              :on-connect (fn [^Session session]
                            (reset! result (.. session getUpgradeRequest getSubProtocols))
                            (.release sem)))]
    (with-timeout (.acquire sem))
    (is (= @result ["wamp"]))
    (close conn)))

(deftest extensions-test
  (let [result (atom nil)
        sem (java.util.concurrent.Semaphore. 0)
        conn (connect
               uri
               :extensions ["permessage-deflate"]
               :on-connect (fn [^Session session]
                             (reset! result (.. session getUpgradeRequest getExtensions))
                             (.release sem)))]
    (with-timeout (.acquire sem))
    (is (= (-> @result (.get 0) (.getName))
           "permessage-deflate"))
    (close conn)))
