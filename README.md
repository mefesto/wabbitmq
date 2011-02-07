# WabbitMQ

WabbitMQ is a Clojure messaging library for use with RabbitMQ. It wraps RabbitMQ's Java AMQP client
library (v2.2.0).

## Usage

First add the following to your `project.clj`:

    [com.mefesto/wabbitmq "0.1.0"]

Next, let's setup a binding between a test exchange and queue. You could do the binding within your
producer/consumer code but I like to separate this out so the producer/consumers only need to be concerned
with their respective exchanges or queues and not how they are bound. For example, you can start out with
a `direct` exchange and later change to a `topic` with routing-key pattern matching without impacting the
consumers.

    (use 'com.mefesto.wabbitmq)

    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (with-channel
        (exchange-declare "test.exchange" "direct")
        (queue-declare "test.queue")
        (queue-bind "test.queue" "test.exchange" "test")))

Now let's implement a simple producer for our test exchange:

    (use 'com.mefesto.wabbitmq)

    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (with-channel
        (with-exchange "test.exchange"
          (publish "test" (.getBytes "Hello world!"))))) ; test is the routing-key

And here is a simple consumer:

    (use 'com.mefesto.wabbitmq)

    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (with-channel
        (with-queue "test.queue"
          (doseq [msg (consuming-seq true)] ; consumes messages with auto-acknowledge enabled
            (println "received:" (String. (:body msg)))))))

WabbitMQ depends on RabbitMQ's Java client which passes messages around as a byte-array. It would be more
convenient to pass messages around as strings or objects and let the library handle the conversion. So, WabbitMQ 
allows you to provide different content-type handlers for altering the message body. Below is an example:

    ;; example producer
    (use 'com.mefesto.wabbitmq
         'com.mefesto.wabbitmq.content-type)

    (def supported-content-types
      [application-json])

    (def props {:content-type "application/json"})

    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (with-channel {:content-types supported-content-types}
        (with-exchange "test.exchange"
          (publish "test" props {:fname "Allen" :lname "Johnson"}))))

    ;; example consumer
    (use 'com.mefesto.wabbitmq
         'com.mefesto.wabbitmq.content-type)

    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (with-channel {:content-types [application-json]}
        (with-queue "test.queue"
          (doseq [{body :body} (consuming-seq true)]
            (println (str "received: firstname=" (:fname body) ", lastname=" (:lname body)))))))

A content-type handler is a vector of three functions:

  1. A test to determine if this handler supports the given content-type.
  2. An encoding function that takes the message and converts it to a byte-array.
  3. A decoding function that takes a byte-array and converts it to some data type.

Provided are basic implementations for text/plain, application/json and application/clojure. Take a look
at `src/com/mefesto/wabbitmq/content_type.clj` for more information about content-type handlers.


You should use a separate channel per thread. Here is an example of a consumer using multiple threads to 
process messages:

    ;; producer
    (use 'com.mefesto.wabbitmq
         'com.mefesto.wabbitmq.content-type)
    
    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (with-channel {:content-types [text-plain]}
        (with-exchange "test.exchange"
          (dotimes [_ 10]
            (publish "test" {:content-type "text/plain"} "Hello, world!")))))


    ;; consumer
    (use 'com.mefesto.wabbitmq
         'com.mefesto.wabbitmq.content-type)
    
    (def num-consumers 5)
    
    (defn consumer []
      (with-channel {:content-types [text-plain]}
        (with-queue "test.queue"
          (doseq [{body :body} (consuming-seq true)]
            (println (str (Thread/currentThread) " received: " body))))))
    
    (with-broker {:host "localhost" :username "guest" :password "guest"}
      (invoke-consumers num-consumers consumer))

### Broker configuration options

For use with the `with-broker` macro:

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

This is a Clojure map of the following properties. This map is converted to an instance of
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

`consuming-seq` pulls messages from the currently bound queue using the `com.rabbitmq.client.QueueingConsumer`.
The full function signature is `(consuming-seq auto-ack? timeout)` with overridden versions with the following
defaults:

  * `auto-ack?` boolean indicating if messages are auto-acknowledged (default: false)
  * `timeout` Block for the given timeout in milliseconds. A value of zero blocks indefinitely. (default: 0)

## Development

In order to run tests you'll need RabbitMQ locally installed. The tests will try to connect with the
following configuration:

    {:host "localhost"
     :username "guest"
     :password "guest"
     :virtual-host "/test"}

## TODO

  * Documentation, especially in the source code.
  * Better tests
  * Better handling of multiple channels (threads)
  * Better error handling
  * Bug fixes, enhancements as they are identified

## License

Copyright (C) 2010 Allen Johnson

Distributed under the Eclipse Public License, the same as Clojure. See the file COPYING.
