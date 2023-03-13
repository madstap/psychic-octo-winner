(ns kitchen.ingest-orders-test
  (:require
   [clojure.test :refer [deftest is]]
   [kitchen.ingest-orders :as ingest-orders]
   [integrant.core :as ig]
   [clojure.core.async :as async]))

(deftest json-file-ingestor-test
  (let [ingester (ig/init-key ::ingest-orders/json-file-ingester
                              {:file-path "test/orders.json"
                               :ingest-rate 1000})
        orders-chan (async/chan)
        ingester-chan (ingester orders-chan)]
    (is (= [{:id #uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd",
             :name "Banana Split",
             :temp :frozen,
             :shelf-life 20,
             :decay-rate 0.63}
            {:id #uuid "7f682ade-8375-4ef4-aa9b-45440f361268",
             :name "Kebab",
             :temp :hot,
             :shelf-life 200,
             :decay-rate 0.54}]
           (repeatedly 2 #(async/<!! orders-chan))))
    (is (nil? (async/poll! orders-chan)))
    (is (nil? (async/<!! ingester-chan)))))
