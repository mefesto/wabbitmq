(ns com.mefesto.test-connection-factory
  (:use [clojure.test])
  (:require [com.mefesto.wabbitmq :as mq])
  (:import [com.rabbitmq.client ConnectionFactory]))

(def uri "amqp://testuser:testpass@somehost:4321/temp")

(deftest connection-by-uri
  (let [factory (#'mq/connection-factory {:uri uri})]
    (is (= "somehost" (.getHost factory)))
    (is (= 4321 (.getPort factory)))
    (is (= "temp" (.getVirtualHost factory)))
    (is (= "testuser" (.getUsername factory)))
    (is (= "testpass" (.getPassword factory)))))
