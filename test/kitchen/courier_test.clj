(ns kitchen.courier-test
  (:require [kitchen.courier :as courier]
            [clojure.test :refer [deftest is]]
            [matcher-combinators.test :refer [match?]]
            [clojure.core.async :as async]
            [kitchen.shelves :as shelves]
            [tick.core :as tick]))

(def now (tick/instant "2023-03-13T00:00:00Z"))

(def order-id #uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd")

(def order
  {:id order-id
   :name "Banana Split",
   :temp :frozen,
   :shelf-life 20,
   :cooked-at now
   :decay-rate 0.63})

(def overflow-id #uuid "7f682ade-8375-4ef4-aa9b-45440f361268")

(def overflow-order
  {:id overflow-id,
   :name "Kebab",
   :temp :hot,
   :shelf-life 200,
   :cooked-at now
   :decay-rate 0.54})

(def discarded-id #uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd")

(def discarded-order
  {:id discarded-id
   :name "Banana Split",
   :temp :frozen,
   :shelf-life 20,
   :decay-rate 0.63})

(deftest courier-test
  (let [orders-to-pickup-chan (async/chan)
        state (-> shelves/empty-state
                  (shelves/put-on-temp-shelf order)
                  (shelves/put-on-overflow-shelf overflow-order))
        *state (atom state)
        *states (atom [])
        _ (add-watch *state ::spy #(swap! *states conj %4))
        ctx {:pickup-time [0.001 0.002]
             :*state *state
             :log (fn [_ _])
             :orders-to-pickup-chan orders-to-pickup-chan}
        courier-chan (courier/pickup-orders! ctx)]
    (async/>!! orders-to-pickup-chan order)
    ;; Sleep long enough that we know the previous courier has finished
    (Thread/sleep 10)
    (async/>!! orders-to-pickup-chan overflow-order)
    (Thread/sleep 10)
    (async/>!! orders-to-pickup-chan discarded-order)
    (async/close! orders-to-pickup-chan)
    (async/<!! courier-chan)
    (let [[s1 s2 s3] @*states]
      (is (match? {:shelves {:overflow {overflow-id overflow-order}}
                   :event [:pickup/succeeded {:id order-id}]}
                  s1))
      (is (= {:shelves shelves/empty-shelves
              :event [:pickup/succeeded {:id overflow-id}]}
             s2))
      (is (= {:shelves shelves/empty-shelves
              :event [:pickup/failed {:id discarded-id}]}
             s3)))))
