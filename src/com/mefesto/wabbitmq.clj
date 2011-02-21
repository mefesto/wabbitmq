(ns com.mefesto.wabbitmq
  (:import [com.rabbitmq.client AMQP$BasicProperties Address ConnectionFactory Envelope
            QueueingConsumer QueueingConsumer$Delivery]
           [java.util.concurrent Executors]))

;;; connection functions
(def ^{:dynamic true}
  *connection* nil)

(defn- connection []
  (or *connection*
      (throw (IllegalStateException. "No connection bound! Are you using `with-broker'?"))))

(def ^{:private true}
  connection-defaults
  {:host "localhost"
   :port -1
   :virtual-host "/"
   :username "guest"
   :password "guest"
   :requested-channel-max ConnectionFactory/DEFAULT_CHANNEL_MAX
   :requested-frame-max ConnectionFactory/DEFAULT_FRAME_MAX
   :requested-heartbeat ConnectionFactory/DEFAULT_HEARTBEAT
   :addresses nil})

(defn- connection-factory [config]
  (let [cfg (merge connection-defaults config)]
    (doto (ConnectionFactory.)
      (.setHost (:host cfg))
      (.setPort (:port cfg))
      (.setVirtualHost (:virtual-host cfg))
      (.setUsername (:username cfg))
      (.setPassword (:password cfg))
      (.setRequestedChannelMax (:requested-channel-max cfg))
      (.setRequestedFrameMax (:requested-frame-max cfg))
      (.setRequestedHeartbeat (:requested-heartbeat cfg)))))

(defn- make-connection [{addrs :addresses :as config}]
  (let [factory (connection-factory config)]
    (if addrs
      (.newConnection factory (into-array Address addrs))
      (.newConnection factory))))

(defn with-broker* [config f]
  (with-open [conn (make-connection config)]
    (binding [*connection* conn]
      (f))))

(defmacro with-broker [cfg & body]
  `(with-broker* ~cfg (fn [] ~@body)))

;;; content-type encoding/decoding
(def ^{:dynamic true}
  *content-types* nil)

(defn- encode [props data]
  (if-let [content-type (:content-type props)]
    (if-let [encoder (first (for [[pred enc _] *content-types* :when (pred content-type)] enc))]
      (encoder content-type data)
      data)
    data))

(defn- decode [props data]
  (if-let [content-type (:content-type props)]
    (if-let [decoder (first (for [[pred _ dec] *content-types* :when (pred content-type)] dec))]
      (decoder content-type data)
      data)
    data))

;;; channel functions
(def ^{:dynamic true}
  *channel* nil)

(defn- channel []
  (or *channel*
      (throw (IllegalStateException. "No channel bound! Are you using `with-channel'?"))))

(def ^{:private true}
  channel-defaults
  {:num nil
   :content-types nil})

(defn- make-channel [{:keys [num confirm-listener default-consumer flow-listener return-listener] :as cfg}]
  (let [chan (if num
               (.createChannel (connection) num)
               (.createChannel (connection)))]
    (when confirm-listener
      (.setConfirmListener confirm-listener))
    (when default-consumer
      (.setDefaultConsumer chan default-consumer))
    (when flow-listener
      (.setFlowListener chan flow-listener))
    (when return-listener
      (.setReturnListener chan return-listener))
    chan))

(defn with-channel* [cfg f]
  (let [config (merge channel-defaults cfg)]
    (with-open [chan (make-channel config)]
      (binding [*channel* chan
                *content-types* (:content-types config)]
        (f)))))

(defmacro with-channel [& forms]
  (let [config (if (map? (first forms)) (first forms))
        body   (if (map? (first forms)) (rest forms) forms)]
    `(with-channel* ~config (fn [] ~@body))))

;;; basic properties
(defn- props->map [^AMQP$BasicProperties props]
  {:app-id (.getAppId props)
   :class-id (.getClassId props)
   :class-name (.getClassName props)
   :cluster-id (.getClusterId props)
   :content-encoding (.getContentEncoding props)
   :content-type (.getContentType props)
   :correlation-id (.getCorrelationId props)
   :delivery-mode (.getDeliveryMode props)
   :expiration (.getExpiration props)
   :headers (if-let [hdrs (.getHeaders props)]
              (zipmap (map keyword (keys hdrs))
                      (vals hdrs)))
   :message-id (.getMessageId props)
   :priority (.getPriority props)
   :reply-to (.getReplyTo props)
   :timestamp (.getTimestamp props)
   :type (.getType props)
   :user-id (.getUserId props)})

(defn- map->props [amap]
  (doto (AMQP$BasicProperties.)
    (.setAppId (:app-id amap))
    (.setClusterId (:cluster-id amap))
    (.setContentEncoding (:content-encoding amap))
    (.setContentType (:content-type amap))
    (.setCorrelationId (:correlation-id amap))
    (.setDeliveryMode (:delivery-mode amap))
    (.setExpiration (:expiration amap))
    (.setHeaders (if-let [hdrs (:headers amap)]
                   (java.util.HashMap.
                    (zipmap (map name (keys hdrs))
                            (vals hdrs)))))
    (.setMessageId (:message-id amap))
    (.setPriority (:priority amap))
    (.setReplyTo (:reply-to amap))
    (.setTimestamp (:timestamp amap))
    (.setType (:type amap))
    (.setUserId (:user-id amap))))

;;; exchange functions
(def ^{:dynamic true}
  *exchange* nil)

(defn- exchange []
  (or *exchange*
      (throw (IllegalStateException. "No exchange bound! Are you using `with-exchange'?"))))

(def ^{:private true}
  exchange-defaults
  {:name ""
   :type "direct"
   :passive? false
   :durable? false
   :auto-delete? false
   :args nil}) 

(defn- make-exchange [cfg]
  (if (instance? String cfg)
    (assoc exchange-defaults :name cfg :passive? true)
    (merge exchange-defaults cfg)))

(declare exchange-declare exchange-declare-passive)

(defn- exchange-declare-internal []
  (when-not (= (:name (exchange)) "") ; skip if default exchange
    (if (:passive? (exchange))
      (exchange-declare-passive (:name (exchange)))
      (let [{:keys [name type durable? auto-delete? args]} (exchange)]
        (exchange-declare name type durable? auto-delete? args)))))

(defn with-exchange* [config f]
  (binding [*exchange* (make-exchange config)]
    (exchange-declare-internal)
    (f)))

(defmacro with-exchange [cfg & body]
  `(with-exchange* ~cfg (fn [] ~@body)))

;;; queue functions
(def ^{:dynamic true}
  *queue* nil)

(defn- queue []
  (or *queue*
      (throw (IllegalStateException. "No queue bound! Are you using `with-queue'?"))))

(def ^{:private true}
  queue-defaults
  {:name nil
   :passive? false
   :durable? false
   :exclusive? false
   :auto-delete? false
   :args nil})

(defn- make-queue [config]
  (cond
   (instance? String config) (assoc queue-defaults :name config :passive? true)
   (map? config) (merge queue-defaults config)
   :else queue-defaults))

(declare queue-declare queue-declare-passive)

(defn- queue-declare-internal []
  (if (:passive? (queue))
    (queue-declare-passive (:name (queue)))
    (let [{:keys [name durable? exclusive? auto-delete? args]} (queue)]
      (queue-declare name durable? exclusive? auto-delete? args))))

(defn with-queue* [config f]
  (binding [*queue* (make-queue config)]
    (queue-declare-internal)
    (f)))

(defmacro with-queue [cfg & body]
  `(with-queue* ~cfg (fn [] ~@body)))

;;; channel commands
(defn abort
  ([] (.abort (channel)))
  ([code msg] (.abort (channel) code msg)))

(defn flow [active?]
  (.flow (channel) active?))

(defn qos
  ([prefetch-count]
     (.basicQos (channel) prefetch-count))
  ([prefetch-size prefetch-count global]
     (.basicQos (channel) prefetch-size prefetch-count global)))

(defn tx-commit []
  (.txCommit (channel)))

(defn tx-rollback []
  (.txRollback (channel)))

(defn tx-select []
  (.txSelect (channel)))

;;; exchange commands
(defn exchange-bind
  ([dest src routing-key]
     (.exchangeBind (channel) dest src routing-key))
  ([dest src routing-key args]
     (.exchangeBind (channel) dest src routing-key args)))

(defn exchange-declare
  ([exchange type]
     (exchange-declare exchange type false))
  ([exchange type durable?]
     (exchange-declare exchange type durable? false))
  ([exchange type durable? auto-delete?]
     (exchange-declare exchange type durable? auto-delete? nil))
  ([exchange type durable? auto-delete? args]
     (.exchangeDeclare (channel) exchange type durable? auto-delete? args)))

(defn exchange-declare-passive [exchange]
  (.exchangeDeclarePassive (channel) exchange))

(defn exchange-delete
  ([exchange]
     (.exchangeDelete (channel) exchange))
  ([exchange if-unused?]
     (.exchangeDelete (channel) exchange if-unused?)))

(defn exchange-unbind
  ([dest src routing-key]
     (.exchangeUnbind (channel) dest src routing-key))
  ([dest src routing-key args]
     (.exchangeUnbind (channel) dest src routing-key args)))

(defn publish
  ([routing-key body]
     (publish routing-key nil body))
  ([routing-key props body]
     (publish routing-key false false props body))
  ([routing-key mandatory? immediate? props body]
     (let [exname (:name (exchange))]
       (.basicPublish (channel) exname routing-key mandatory? immediate?
                      (map->props props)
                      (encode props body)))))

;;; queue commands
(defn queue-bind
  ([queue exchange routing-key]
     (.queueBind (channel) queue exchange routing-key))
  ([queue exchange routing-key args]
     (.queueBind (channel) queue exchange routing-key args)))

(defn queue-declare
  ([] (.queueDeclare (channel)))
  ([queue]
     (queue-declare queue false))
  ([queue durable?]
     (queue-declare queue durable? false))
  ([queue durable? exclusive?]
     (queue-declare queue durable? exclusive? false))
  ([queue durable? exclusive? auto-delete?]
     (queue-declare queue durable? exclusive? auto-delete? nil))
  ([queue durable? exclusive? auto-delete? args]
     (.queueDeclare (channel) queue durable? exclusive? auto-delete? args)))

(defn queue-declare-passive [queue]
  (.queueDeclarePassive (channel) queue))

(defn queue-delete
  ([queue]
     (.queueDelete (channel) queue))
  ([queue if-unused? if-empty?]
     (.queueDelete (channel) queue if-unused? if-empty?)))

(defn queue-purge
  ([]
     (.queuePurge (channel) (:name (queue))))
  ([queue]
     (.queuePurge (channel) queue)))

(defn queue-unbind
  ([queue exchange routing-key]
     (.queueUnbind (channel) queue exchange routing-key))
  ([queue exchange routing-key args]
     (.queueUnbind (channel) queue exchange routing-key args)))

(defn ack
  ([delivery-tag]
     (ack delivery-tag false))
  ([delivery-tag multiple?]
     (.basicAck (channel) delivery-tag multiple?)))

(defn cancel [tag]
  (.basicCancel (channel) tag))

(defn consume
  ([auto-ack? callback]
     (.basicConsume (channel) (:name (queue)) auto-ack? callback))
  ([auto-ack? tag callback]
     (consume auto-ack? tag nil callback))
  ([auto-ack? tag args callback]
     (consume auto-ack? tag false false args callback))
  ([auto-ack? tag no-local? exclusive? args callback]
     (let [qname (:name (queue))]
       (.basicConsume (channel) qname auto-ack? tag no-local? exclusive? args callback))))

(defn queue-get
  ([] (get false))
  ([auto-ack?]
     (.basicGet (channel) (:name (queue)) auto-ack?)))

(defn recover
  ([] (recover true))
  ([requeue?]
     (.basicRecover (channel) requeue?)))

(defn reject
  ([tag] (reject tag false))
  ([tag requeue?]
     (.basicReject (channel) tag requeue?)))

(defn- as-envelope [^Envelope env]
  (when env
    {:delivery-tag (.getDeliveryTag env)
     :exchange (.getExchange env)
     :routing-key (.getRoutingKey env)
     :redelivered? (.isRedeliver env)}))

(defn- as-message [^QueueingConsumer$Delivery delivery]
  (when delivery
    (let [body  (.getBody delivery)
          env   (as-envelope (.getEnvelope delivery))
          props (props->map (.getProperties delivery))]
      {:body (decode props body)
       :envelope env
       :props props})))

(defn- next-delivery [^QueueingConsumer consumer timeout]
  (if (> timeout 0)
    (.nextDelivery consumer timeout)
    (.nextDelivery consumer)))

(defn consuming-seq
  ([]
     (consuming-seq false))
  ([auto-ack?]
     (consuming-seq auto-ack? 0))
  ([auto-ack? timeout]
     (let [consumer (QueueingConsumer. (channel))]
       (consume auto-ack? consumer)
       ((fn message-seq []
          (lazy-seq
           (cons (as-message (next-delivery consumer timeout))
                 (message-seq))))))))

(defn invoke-consumers [n consumer]
  (let [pool (Executors/newFixedThreadPool n)
        workers (repeatedly n #(bound-fn* consumer))]
    (doseq [future (.invokeAll pool workers)]
      (.get future))
    (.shutdown pool)))
