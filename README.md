# Gniazdo

[Gniazdo][def] is a [WebSocket][ws] client for Clojure. Its main purpose is
testing WebSockets services without a browser. It uses [Jetty's][jetty]
implementation of the WebSocket protocol.

## Usage

```clojure
(require [gniazdo.core :as ws])
(def socket
  (ws/connect
    "ws://example.org:1234/socket"
    :on-receive #(prn 'received %)))
(ws/send-msg socket "hello")
(ws/close socket)
```

### `(gniazdo.core/connect uri & callbacks)`

`gniazdo.core/connect` opens a WebSocket connection using a
given `uri`. Functions handling WebSocket events should be specified using
the `callbacks` map. Following keys are valid:

 - `:on-connect` – a unary function called after the connection has been
   established. The handler is a [`Session`][session] instance.
 - `:on-receive` – a unary function called when a message is received. The
   argument is a received `String`.
 - `:on-error` – a unary function called on in case of errors. The argument is
   a `Throwable` describing the error.
 - `:on-close` – a binary function called when the connection is closed.
   Arguments are an `int` status code and a `String` description of reason.

`gniazdo.core/connect` returns an opaque representation of the connection.

See also [WebSocketListener][listener].

### `(gniazdo.core/send-msg [conn message])`

`gniazdo.core/send-msg` sends a given message using a connection established
with `gniazdo.core/connect`. The message should be a `String`.

### `(gniazdo.core/close [conn])`

`gniazdo.core/close` closes a connection established with
`gniazdo.core/connect`.

## License

    Copyright 2013 stylefruits GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[def]: https://en.wiktionary.org/wiki/gniazdo
[ws]: https://en.wikipedia.org/wiki/WebSocket
[jetty]: http://www.eclipse.org/jetty/
[session]: http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/websocket/api/Session.html
[listener]: http://download.eclipse.org/jetty/stable-9/apidocs/org/eclipse/jetty/websocket/api/WebSocketListener.html
