(ns kitchen.kitchen
  (:require
   [clojure.core.async :as async]
   [kitchen.courier :as courier]
   [kitchen.ingest-orders :as ingest-orders]
   [kitchen.shelves :as shelves]
   [tick.core :as tick]))

(defn cook-orders!
  "The loop that cooks orders and puts them on the shelves.
  Will run until `orders-to-cook-chan` closes."
  [{:keys [orders-to-cook-chan *state log] :as ctx}]
  (async/go-loop []
    (when-some [order (async/<! orders-to-cook-chan)]
      (let [now (tick/now)]
        (shelves/cook-and-shelve! *state log order now)
        (recur)))))

(defn make-channels
  "Creates the main channels of the application.
  orders-chan is the input channel, where new orders come in.
  The orders-chan is fanned out to orders-to-cook-chan and orders-to-pickup-chan."
  []
  (let [orders-chan (async/chan)
        orders-to-cook-chan (async/chan)
        orders-to-pickup-chan (async/chan)]
    (doto (async/mult orders-chan)
      (async/tap orders-to-pickup-chan)
      (async/tap orders-to-cook-chan))
    {:orders-chan orders-chan
     :orders-to-cook-chan orders-to-cook-chan
     :orders-to-pickup-chan orders-to-pickup-chan}))

(defn start-kitchen!
  "The main process. It starts and coordinates the subprocesses for
  ingesting, cooking/shelving and picking up orders.

  Returns a map which contains `:kitchen-chan`, a channel that
  will close when it has ran to completion and `:*state`, an atom that
  contains the state of the system."
  [{:keys [log] :as config}]
  (let [*state (atom shelves/empty-state)
        {:keys [orders-chan] :as ctx} (-> config
                                          (merge (make-channels))
                                          (assoc :*state *state))
        _ (cook-orders! ctx)
        courier-chan (courier/pickup-orders! ctx)
        ingester-chan (ingest-orders/ingest! ctx)
        kitchen-chan (async/go
                       (async/<! ingester-chan)
                       (log :kitchen/finished-ingesting (:shelves @*state))
                       (async/close! orders-chan)
                       (async/<! courier-chan)
                       (log :kitchen/finished (:shelves @*state)))]
    {:kitchen-chan kitchen-chan
     :*state *state}))
