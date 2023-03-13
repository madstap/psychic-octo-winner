(ns kitchen.kitchen-test
  (:require [kitchen.kitchen :as kitchen]
            [clojure.test :refer [deftest is]]
            [clojure.core.async :as async]
            [kitchen.shelves :as shelves]))

(def example-orders
  [{:id #uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd",
    :name "Banana Split",
    :temp :frozen,
    :shelf-life 20,
    :decay-rate 0.63}
   {:id #uuid "7f682ade-8375-4ef4-aa9b-45440f361268",
    :name "Kebab",
    :temp :hot,
    :shelf-life 200,
    :decay-rate 0.54}])

(deftest start-kitchen!-test
  (let [*logs (atom [])
        ctx {:orders example-orders
             :ingest-rate 1000
             :pickup-time [0.001 0.002]
             :log (fn [event data]
                    (swap! *logs conj [event data]))}
        {:keys [kitchen-chan *state]} (kitchen/start-kitchen! ctx)]
    (async/<!! kitchen-chan)
    (let [logs @*logs]
      (is (match? (map (fn [{:keys [id]}]
                         [:shelf/placed-on-correct-shelf {:id id}])
                       example-orders)
                  (filter #(= :shelf/placed-on-correct-shelf (first %))
                          logs)))
      (is (match? (map (fn [{:keys [id]}]
                         [:pickup/succeeded {:id id}])
                       example-orders)
                  (filter #(= :pickup/succeeded (first %))
                          logs)))
      (is (empty? (filter #(= :pickup/failed (first %)) logs)))
      (is (= :kitchen/finished (first (last logs))))
      (is (= shelves/empty-state
             (dissoc @*state :event))))))