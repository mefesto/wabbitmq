(ns com.mefesto.wabbitmq.test-content-type
  (:use [clojure.contrib.json :only (json-str)]
        [clojure.test]
        [com.mefesto.wabbitmq.content-type])
  (:import [java.util Arrays]))

(deftest text-plain-supported?
  (is (true? (text-plain? "text/plain")))
  (is (true? (text-plain? "text/plain; charset=UTF-8")))
  (is (true? (text-plain? "text/blah; charset=UTF-16")))
  (is (false? (text-plain? "application/json")))
  (is (false? (text-plain? "application/octet-stream"))))

(deftest text-plain-encoding
  (is (Arrays/equals (.getBytes "hello")
                     (text-plain-encode "text/plain" "hello")))
  (is (Arrays/equals (.getBytes "hello" "utf16")
                     (text-plain-encode "text/plain; charset=UTF-16" "hello"))))

(deftest text-plain-decoding
  (is (= "hello" (text-plain-decode "text/plain" (.getBytes "hello"))))
  (is (= "hello" (text-plain-decode "text/plain; charset=utf-16" (.getBytes "hello" "utf16")))))

(deftest application-json-supported?
  (is (true? (application-json? "application/json")))
  (is (true? (application-json? "application/json; charset=UTF-8")))
  (is (false? (application-json? "application/other"))))

(deftest application-json-encoding
  (is (Arrays/equals (-> (json-str [1 2 3]) (.getBytes))
                     (application-json-encode "application/json" [1 2 3])))
  (is (Arrays/equals (-> (json-str [1 2 3]) (.getBytes "utf16"))
                     (application-json-encode "application/json; charset=UTF-16" [1 2 3]))))

(deftest application-json-decoding
  (is (= [1 2 3]
           (application-json-decode "application/json"
                                    (-> (json-str [1 2 3]) (.getBytes)))))
  (is (= [1 2 3]
           (application-json-decode "application/json; charset=UTF-16"
                                    (-> (json-str [1 2 3]) (.getBytes "utf16"))))))

(deftest application-clojure-supported?
  (is (true? (application-clojure? "application/clojure")))
  (is (true? (application-clojure? "application/clojure; charset=UTF-16")))
  (is (false? (application-clojure? "application/json"))))

(deftest application-clojure-encoding
  (is (Arrays/equals (-> (pr-str [1 2 3]) (.getBytes))
                     (application-clojure-encode "application/clojure" [1 2 3])))
  (is (Arrays/equals (-> (pr-str [1 2 3]) (.getBytes "utf-16"))
                     (application-clojure-encode "application/clojure; charset=utf-16" [1 2 3]))))

(deftest application-clojure-decoding
  (is (= [1 2 3]
           (application-clojure-decode "application/clojure"
                                       (-> (pr-str [1 2 3]) (.getBytes)))))
  (is (= [1 2 3]
           (application-clojure-decode "application/clojure; charset=utf-16"
                                       (-> (pr-str [1 2 3]) (.getBytes "utf-16"))))))
