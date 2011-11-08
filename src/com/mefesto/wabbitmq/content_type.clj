(ns com.mefesto.wabbitmq.content-type
  (:use [cheshire.core :only [generate-string parse-string]]))

(defn- charset [type]
  (second (re-find #"charset=([^;]+)" type)))

;; text/plain support
(defn text-plain? [content-type]
  (not (nil? (re-find #"^text/.*" content-type))))

(defn text-plain-encode [content-type data]
  (when data
    (if-let [charset (charset content-type)]
      (.getBytes data charset)
      (.getBytes data))))

(defn text-plain-decode [content-type data]
  (when data
    (if-let [charset (charset content-type)]
      (String. data charset)
      (String. data))))

(def text-plain [text-plain? text-plain-encode text-plain-decode])

;; application/json support
(defn application-json? [content-type]
  (not (nil? (re-find #"^application/json" content-type))))

(defn application-json-encode [content-type data]
  (when data
    (if-let [charset (charset content-type)]
      (-> (generate-string data) (.getBytes charset))
      (-> (generate-string data) (.getBytes)))))

(defn application-json-decode [content-type data]
  (when data
    (if-let [charset (charset content-type)]
      (-> (String. data charset) (parse-string true))
      (-> (String. data) (parse-string true)))))

(def application-json [application-json? application-json-encode application-json-decode])

;; application/clojure support
(defn application-clojure? [content-type]
  (not (nil? (re-find #"^application/clojure" content-type))))

(defn application-clojure-encode [content-type data]
  (when data
    (if-let [charset (charset content-type)]
      (-> (pr-str data) (.getBytes charset))
      (-> (pr-str data) (.getBytes)))))

(defn application-clojure-decode [content-type data]
  (when data
    (if-let [charset (charset content-type)]
      (-> (String. data charset) (read-string))
      (-> (String. data) (read-string)))))

(def application-clojure
  [application-clojure?
   application-clojure-encode
   application-clojure-decode])
