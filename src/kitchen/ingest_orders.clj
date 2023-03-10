(ns kitchen.ingest-orders
  (:require [clojure.data.json :as json]
            [clojure.core.async :as async]
            [clojure.java.io :as io]
            [camel-snake-kebab.core :as csk]))

(defn parse-order
  "Takes an order and parses the values from strings to proper clojure types."
  [order]
  (-> order
      (update :temp keyword)
      (update :id parse-uuid)))

(defn read-json-file
  "Takes the name of a json file assumed to contain an array of order objects
  and returns a sequence of orders."
  [file-name]
  (map parse-order (-> (io/reader file-name)
                       (json/read :key-fn csk/->kebab-case-keyword))))

(defn ingest-orders
  "Puts each order from `orders` (a seqable of maps) onto `orders-chan` at a
  rate of `ingest-rate` orders per second. Returns a channel that closes after
  all orders have been ingested."
  [orders-chan ingest-rate orders]
  (let [ingest-wait-ms (/ 1000 ingest-rate)]
    (async/go-loop [[order & more-orders] (seq orders)]
      (when (some? order)
        (async/>! orders-chan order)
        (async/<! (async/timeout ingest-wait-ms))
        (recur more-orders)))))


(comment

  (def ch (async/chan))

  (async/go-loop []
    (when-some [order (async/<! ch)]
      (clojure.pprint/pprint order)
      (println)
      (recur)))

  (async/go
    (async/<! (ingest-orders ch 2 (read-json-file "orders.json")))
    (println "Finished ingesting orders"))

  )
