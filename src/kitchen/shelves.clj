(ns kitchen.shelves
  "This namespace implements the operations on the *state atom.")

(def shelf-capacity
  {:hot 10
   :cold 10
   :frozen 10
   :overflow 15})

(def empty-shelves
  (update-vals shelf-capacity (constantly {})))

(def empty-state
  {:shelves empty-shelves})

(defn cook-order
  "Cooks an order"
  [order now]
  (assoc order :cooked-at now))

(defn has-space?
  "Check if the specified shelf has space."
  [shelves shelf]
  (< (count (get shelves shelf)) (shelf-capacity shelf)))

(defn overflow-orders-with-shelf-space
  "Given the state of the shelves, returns a sequence of all the orders from the
  overflow shelf that can be moved back to the right temperature shelf."
  [{:keys [overflow] :as shelves}]
  (not-empty (filter (fn [{:keys [temp]}]
                       (has-space? shelves temp))
                     (vals overflow))))

(defn best-overflow-order-to-replace [orders now]
  ;; TODO: Choose better
  (rand-nth orders))

(defn put-on-temp-shelf
  [state {:keys [temp id] :as order}]
  (assoc-in state [:shelves temp id] order))

(defn put-on-overflow-shelf [state {:keys [id] :as order}]
  (assoc-in state [:shelves :overflow id] order))

(defn discard-overflow-order [state {:keys [id]}]
  (update-in state [:shelves :overflow] dissoc id))

(defn cook-and-shelve
  [{:keys [shelves] :as state} order now]
  (let [{:keys [id temp] :as cooked-order} (cook-order order now)]
    (if (has-space? shelves temp)
      (-> state
          (put-on-temp-shelf cooked-order)
          (assoc :event [:shelf/placed-on-correct-shelf {:id id}]))
      (if (has-space? shelves :overflow)
        (-> state
            (put-on-overflow-shelf cooked-order)
            (assoc :event [:shelf/placed-on-overflow {:id id}]))
        (if-some [allowable-orders (overflow-orders-with-shelf-space shelves)]
          (let [{overflow-id :id, :as overflow-order}
                (best-overflow-order-to-replace allowable-orders now)]
            (-> state
                (put-on-temp-shelf overflow-order)
                (put-on-overflow-shelf cooked-order)
                (assoc :event [:shelf/placed-on-overflow-replacing-existing
                               {:id id
                                :replaced-overflow-order overflow-id}])))
          (let [waste-order (rand-nth (:overflow shelves))]
            (-> state
                (discard-overflow-order waste-order)
                (put-on-overflow-shelf cooked-order)
                (assoc :event [:shelf/placed-on-overflow-discarding-existing
                               {:id id
                                :discarded-overflow-order (:id waste-order)}]))))))))

(defn order-shelf [shelves {:keys [id]}]
  (some (fn [[shelf orders]]
          (when (contains? orders id)
            shelf))
        shelves))

(defn pickup [{:keys [shelves] :as state} {:keys [id]}]
  (if-some [shelf (order-shelf shelves id)]
    (-> state
        (update-in [:shelves shelf] dissoc id)
        (assoc :event [:pickup/succeeded {:id id}]))
    (-> state
        (assoc :event [:pickup/failed {:id id}]))))

(defn swap-and-log! [*state log f & args]
  (let [{:keys [event shelves]} (apply swap! *state f args)
        [event-name event-data] event]
    (log event-name (assoc event-data :shelves shelves))))

(defn cook-and-shelve!
  "Cook an order and place it on the right shelf."
  [*state log order now]
  (swap-and-log! *state log cook-and-shelve order now))

(defn pickup!
  "Pickup the order from the state"
  [*state log order]
  (swap-and-log! *state log pickup order))
