(ns kitchen.courier
  "Implements the logic for couriers picking up orders. The entrypoint
  to this namespace is the pickup-orders! function."
  (:require
   [clojure.core.async :as async]
   [kitchen.shelves :as shelves]))

(defn random-pickup-ms [[from to]]
  (let [from-ms (* from 1000)
        to-ms (* to 1000)]
    (+ from-ms (rand-int (- to-ms from-ms)))))

(defn dispatch-courier!
  "Dispatches a courier to pick up a specific order. Returns a channel
  that closes when the courier has arrived."
  [{:keys [log pickup-time *state]} *couriers {:keys [id] :as order}]
  (async/go
    (async/<! (async/timeout (random-pickup-ms pickup-time)))
    (shelves/pickup! *state log order)
    (swap! *couriers dissoc id)))

(defn dispatch-couriers!
  "Dispatches couriers when an order is added to the `orders-to-pickup-chan`.
  Returns a channel that closes when the `orders-to-pickup-chan` closes
  and the last courier has been dispatched."
  [{:keys [orders-to-pickup-chan] :as ctx} *couriers]
  (async/go-loop []
    (when-some [{:keys [id] :as order} (async/<! orders-to-pickup-chan)]
      (let [courier-chan (dispatch-courier! ctx *couriers order)]
        (swap! *couriers assoc id courier-chan)
        (recur)))))

(defn pickup-orders!
  "Starts the process that picks up orders. When an order arrives on
  the `orders-to-pickup-chan` will send a courier to pick it up at a
  random time in seconds between the numbers in `pickup-time` (a
  vector of [min max]). Returns a channel that closes when all
  couriers have arrived."
  [{:keys [log *state] :as ctx}]
  ;; *couriers is where we store the currently active couriers to be
  ;; able to wait for them all to complete. It's a map of order id to
  ;; the chan that represents a single courier.
  (let [*couriers (atom {})]
    (async/go
      (async/<! (dispatch-couriers! ctx *couriers))
      (log :courier/dispatched-all (:shelves @*state))
      (doseq [courier-chan (vals @*couriers)]
        (async/<! courier-chan))
      (log :courier/finished-all-pickups (:shelves @*state)))))
