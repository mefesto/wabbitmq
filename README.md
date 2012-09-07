# NOTE: This project is no longer being maintained

This was one of my first clojure projects and it was fun.  Its design
is based on similar patterns found in the old `clojure.contrib.sql`
(clojure v1.2 days?) by using dynamic vars to manage connections,
channels, etc.  Early on this seemed cool but things quickly became
hard to work with.  Unfortunately, I don't have the time to update this
library with the lessons learned.

This repository remains in case anyone still uses it.  Although, if
you still use it I recommend switching to [langohr][1] which is
actively maintained and better designed or simply use the [RabbitMQ
Java Client][2] directly.

[1]: https://github.com/michaelklishin/langohr
[2]: http://www.rabbitmq.com/java-client.html

# WabbitMQ

WabbitMQ is a Clojure messaging library for use with RabbitMQ.  It
wraps RabbitMQ's Java AMQP client library (v2.8.1).

## Usage

First add the following to your `project.clj`:

    [com.mefesto/wabbitmq "0.2.2"]

Next, let's setup a binding between a test exchange and queue.  You
could do the binding within your producer/consumer code but I like to
separate this out so the producer/consumers only need to be concerned
with their respective exchanges or queues and not how they are bound.
For example, you can start out with a `direct` exchange and later
change to a `topic` with routing-key pattern matching without
impacting the consumers.

```clj
(require '[com.mefesto.wabbitmq :as mq])

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/with-channel
    (mq/exchange-declare "test.exchange" "direct")
    (mq/queue-declare "test.queue")
    (mq/queue-bind "test.queue" "test.exchange" "test")))
```

Now let's implement a simple producer for our test exchange:

```clj
(require '[com.mefesto.wabbitmq :as mq])

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/with-channel
    (mq/with-exchange "test.exchange"
      (mq/publish "test" (.getBytes "Hello world!"))))) ; test is the routing-key
```

And here is a simple consumer:

```clj
(require '[com.mefesto.wabbitmq :as mq])

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/with-channel
    (mq/with-queue "test.queue"
      (doseq [msg (mq/consuming-seq true)] ; consumes messages with auto-acknowledge enabled
        (println "received:" (String. (:body msg)))))))
```

WabbitMQ depends on RabbitMQ's Java client which passes messages
around as a byte-array.  It would be more convenient to pass messages
around as strings or objects and let the library handle the
conversion.  So, WabbitMQ allows you to provide different content-type
handlers for altering the message body.  Below is an example:

```clj
;; example producer
(require '[com.mefesto.wabbitmq :as mq]
         '[com.mefesto.wabbitmq.content-type :as mime])

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/with-channel {:content-types [mime/application-json]}
    (mq/with-exchange "test.exchange"
      (mq/publish "test"
                  {:content-type "application/json"}
                  {:fname "Allen" :lname "Johnson"}))))

;; example consumer
(require '[com.mefesto.wabbitmq :as mq]
         '[com.mefesto.wabbitmq.content-type :as mime])

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/with-channel {:content-types [mime/application-json]}
    (mq/with-queue "test.queue"
      (doseq [{body :body} (mq/consuming-seq true)]
        (printf "received: fname=%s, lname=%s%n" (:fname body) (:lname body))
        (flush)))))
```

A content-type handler is a vector of three functions:

  1. A test to determine if this handler supports the given content-type.
  2. An encoding function that takes the message and converts it to a byte-array.
  3. A decoding function that takes a byte-array and converts it to some data type.

Provided are basic implementations for text/plain, application/json
and application/clojure.  Take a look at
`src/com/mefesto/wabbitmq/content_type.clj` for more information about
content-type handlers.


You should use a separate channel per thread. Here is an example of a
consumer using multiple threads to process messages:

```clj
;; producer
(require '[com.mefesto.wabbitmq :as mq]
         '[com.mefesto.wabbitmq.content-type :as mime])

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/with-channel {:content-types [mime/text-plain]}
    (mq/with-exchange "test.exchange"
      (dotimes [_ 10]
        (mq/publish "test" {:content-type "text/plain"} "Hello, world!")))))

;; consumer
(require '[com.mefesto.wabbitmq :as mq]
         '[com.mefesto.wabbitmq.content-type :as mime])

(def num-consumers 5)

(defn consumer []
  (mq/with-channel {:content-types [mime/text-plain]}
    (mq/with-queue "test.queue"
      (doseq [{body :body} (mq/consuming-seq true)]
        (printf "%s received: %s%n" (Thread/currentThread) body)))))

(mq/with-broker {:uri "amqp://localhost/test"}
  (mq/invoke-consumers num-consumers consumer))
```

### Broker configuration options

For use with the `with-broker` macro:

  * `:uri` Convenience property to set `host`, `port`, `virtual-host`, `username` and `password` using the following format: `amqp://[username:password@]host[:port]/virtual-host` (default: nil)
  * `:host` (default: localhost)
  * `:port` (default: -1)
  * `:virtual-host` (default: /)
  * `:username` (default: guest)
  * `:password` (default: guest)
  * `:requested-channel-max` (default: `com.rabbitmq.client.ConnectionFactory/DEFAULT_CHANNEL_MAX`)
  * `:requested-frame-max` (default: `com.rabbitmq.client.ConnectionFactory/DEFAULT_FRAME_MAX`)
  * `:requested-heartbeat` (default: `com.rabbitmq.client.ConnectionFactory/DEFAULT_HEARTBEAT`)
  * `:addresses` Vector of `com.rabbitmq.client.Address` (default: nil)

### Channel configuration options

For use with the `with-channel` macro:

  * `:num` Request a specific channel number (default: nil)
  * `:content-types` List of content-type handlers (default: nil)
  * `:default-consumer` Instance of `com.rabbitmq.client.Consumer` (default: nil)
  * `:confirm-listener` Instance of `com.rabbitmq.client.ConfirmListenr` (default: nil)
  * `:flow-listener` Instance of `com.rabbitmq.client.FlowListener` (default: nil)
  * `:return-listener` Instance of `com.rabbitmq.client.ReturnListener` (default: nil)

### Exchange configuration options

For use with the `with-exchange` macro:

  * `:name` Exchange name (default: "")
  * `:type` Exchange type (default: "direct")
  * `:passive?` Declare the exchange passively (default: false)
  * `:durable?` Exchange should be durable (default: false)
  * `:auto-delete?` (default: false)
  * `:args` instance of java.util.Map<String, Object> (default: nil)

### Queue configuration options

For use with the `with-queue` macro:

  * `:name` Queue name (default: nil)
  * `:passive?` Declare the queue passively (default: false)
  * `:durable?` Queue should be durable (default: false)
  * `:exclusive?` (default: false)
  * `:auto-delete?` (default: false)
  * `:args` instance of java.util.Map<String, Object> (default: nil)

### Message properties

This is a Clojure map of the following properties. This map is
converted to an instance of
`com.rabbitmq.client.AMQP$BasicProperties`:

  * `:app-id` String
  * `:class-id` int
  * `:class-name` String
  * `:cluster-id` String
  * `:content-encoding` String
  * `:content-type` String - This value is required by content-type handlers (nil for byte-array messages)
  * `:correlation-id` String
  * `:delivery-mode` Integer - Use value 2 for persistent messages
  * `:expiration` String
  * `:headers` java.util.Map<String, Object>
  * `:message-id` String
  * `:priority` Integer
  * `:reply-to` String
  * `:timestamp` java.util.Date
  * `:type` String
  * `:user-id` String

### Consuming messages with consuming-seq

`consuming-seq` pulls messages from the currently bound queue.  The
full function signature is `(consuming-seq auto-ack? timeout)` with
overridden versions with the following defaults:

  * `auto-ack?` boolean indicating if messages are auto-acknowledged (default: false)
  * `timeout` Block for the given timeout in milliseconds. A value of zero blocks indefinitely. (default: 0)

## Development

In order to run tests you'll need RabbitMQ locally installed. The
tests will try to connect with the following configuration:

```clj
{:host "localhost"
 :username "guest"
 :password "guest"
 :virtual-host "test"}

;; or using a uri
{:uri "amqp://localhost/test"}
```

You'll probably have to create the `test` vhost:

    $ sudo rabbitmqctl add_vhost test
    $ sudo rabbitmqctl set_permissions -p test guest . . .

## TODO

  * Documentation, especially in the source code.
  * Better tests
  * Better handling of multiple channels (threads)
  * Better error handling
  * Bug fixes, enhancements as they are identified

## License

Copyright (C) 2012 Allen Johnson

Distributed under the Eclipse Public License, the same as Clojure. See the file COPYING.
