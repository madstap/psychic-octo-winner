(ns kitchen.ingest-orders
  (:require
   [clojure.core.async :as async]
   [integrant.core :as ig]
   [kitchen.util.files :as util.fs]))

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
  (map parse-order (util.fs/read-json-file file-name)))

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

(defmethod ig/init-key ::json-file-ingester [_ {:keys [file-path ingest-rate]}]
  (fn [orders-chan]
    (ingest-orders orders-chan ingest-rate (read-json-file file-path))))


(comment

  (def all-orders (read-json-file "orders.json"))

  )
