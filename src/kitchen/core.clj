(ns kitchen.core
  (:require
   [clojure.core.async :as async]
   [kitchen.kitchen :as kitchen]
   [kitchen.log :as log]))

(def defaults
  {:orders-file "orders.json"
   :ingest-rate 2
   :pickup-time [2 6]
   :log log/stdout-log})

(defn main
  "The entry point to the kitchen simulation."
  [config-args]
  (let [config (merge defaults config-args)]
    (async/<!! (:kitchen-chan (kitchen/start-kitchen! config)))))

(comment

  (main {:orders-file "test/orders.json"})

  (main {:ingest-rate 20})

  )
