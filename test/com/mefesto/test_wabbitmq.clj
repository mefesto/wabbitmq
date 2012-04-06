(ns com.mefesto.test-wabbitmq
  (:use [clojure.test]
        [com.mefesto.wabbitmq]
        [com.mefesto.wabbitmq.content-type :only (application-clojure application-json text-plain)])
  (:import [com.rabbitmq.client QueueingConsumer QueueingConsumer$Delivery]
           [java.io IOException]))

(def *consumer* nil)

(defn pop-msg []
  (:body (first (consuming-seq true 5000))))

(defn do-connect [f]
  (with-broker {:host "localhost" :username "guest" :password "guest" :virtual-host "/test"}
    (with-channel {:content-types [text-plain application-json application-clojure]}
      (f))))

(defn do-bindings [f]
  (exchange-declare "test.exchange" "direct")
  (queue-declare "test.queue")
  (queue-bind "test.queue" "test.exchange" "test")
  (with-exchange "test.exchange"
    (with-queue "test.queue"
      (f)))
  (queue-unbind "test.queue" "test.exchange" "test")
  (queue-delete "test.queue")
  (exchange-delete "test.exchange"))

(use-fixtures :once do-connect)
(use-fixtures :each do-bindings)

(deftest publish-bytes
  (publish "test" (.getBytes "hello"))
  (is (= "hello" (String. (pop-msg)))))

(deftest publish-text
  (publish "test" {:content-type "text/plain"} "hello")
  (is (= "hello" (pop-msg))))

(deftest publish-json
  (publish "test" {:content-type "application/json"} {"fname" "Allen" "lname" "Johnson"})
  (is (= {:fname "Allen" :lname "Johnson"}
         (pop-msg))))

(deftest publish-clj
  (publish "test" {:content-type "application/clojure"} [1 2 3 {:key "val"}])
  (is (= [1 2 3 {:key "val"}]
           (pop-msg))))

(deftest consuming-seq-timeout
  (is (nil? (first (consuming-seq true 5000)))))

(deftest consuming-seq-notimeout
  (let [thread (Thread/currentThread)
        interrupt (fn []
                    (Thread/sleep 10)
                    (.interrupt thread))]
    (.start (Thread. interrupt))
    (is (thrown? RuntimeException (first (consuming-seq true))))))

(deftest consuming-seq-basic
  (dotimes [n 100]
    (publish "test" {:content-type "application/clojure"} n))
  (loop [items (consuming-seq true 5000) n 0]
    (when (< n 100)
      (is (= n (-> items first :body)))
      (recur (next items) (inc n)))))

(deftest message-envelope-conversion
  (publish "test" (.getBytes "hello"))
  (let [msg (first (consuming-seq true))]
    (is (map? msg))
    (is (map? (:envelope msg)))
    (is (not (nil? (-> msg :envelope :delivery-tag))))))

(deftest message-headers
  (publish "test"
           {:headers {"count" 0}}
           (.getBytes "hello"))
  (let [msg (first (consuming-seq true))]
    (is (= (get-in msg [:props :headers :count]) 0))))
