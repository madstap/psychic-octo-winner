(ns kitchen.core-test
  (:require [kitchen.core :as core]
            [clojure.test :refer [deftest testing is]]))


(def order-ids
  [#uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd"
   #uuid "7f682ade-8375-4ef4-aa9b-45440f361268"])


(deftest end-to-end-test
  (testing "The applications reads the file, does the work and finishes"
    (let [*logs (atom [])
          ctx {:orders-file "test/orders.json"
               :ingest-rate 100
               :pickup-time [0.1 0.2]
               :log (fn [event data]
                      (swap! *logs conj [event data]))}]
      (core/main ctx)
      (let [logs @*logs]
        (is (match? (map (fn [id]
                           [:shelf/placed-on-correct-shelf {:id id}])
                         order-ids)
                    (filter #(= :shelf/placed-on-correct-shelf (first %))
                            logs)))
        (is (match? (set (map (fn [id]
                                [:pickup/succeeded {:id id}])
                              order-ids))
                    (set (filter #(= :pickup/succeeded (first %))
                                 logs))))
        (is (= :kitchen/finished (first (last logs))))))))
