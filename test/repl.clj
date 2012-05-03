(require '[com.mefesto.wabbitmq :as mq]
         '[com.mefesto.wabbitmq.content-type :as ct]))

(def broker
  {:host "localhost"
   :username "guest"
   :password "guest"
   :virtual-host "/"})

(def queue (mq/publish-queue))

;; setup a publisher thread
(def publisher
  (future
    (mq/with-broker broker
      (mq/with-channel {:content-types [ct/application-json]}
        (mq/with-exchange "test"
          (println "draining publish queue")
          (mq/drain! queue)
          (println "publish queue closed"))))))

;; setup a consumer thread
(def consumer
  (future
    (mq/with-broker broker
      (mq/with-channel {:content-types [ct/application-json]}
        (mq/with-queue "test"
          (println "consumer ready.")
          (->> (mq/consuming-seq true)
               (map :body)
               (take-while #(not= "quit" (:type %)))
               (map #(println "consuming:" %))
               (dorun))
          (println "consumer done."))))))

;; publish test data
(def props {:content-type "application/json"})
(mq/put! queue "" props {:type :doc :msg "Hello world"})
(mq/put! queue "" props {:type :doc :msg "Goodbye world"})
(mq/put! queue "" props {:type :quit})
(mq/close queue)
