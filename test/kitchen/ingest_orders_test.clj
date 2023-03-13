(ns kitchen.ingest-orders-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [kitchen.ingest-orders :as ingest-orders]
   [clojure.core.async :as async]))

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

(deftest json-file-ingestor-test
  (testing "Ingest orders from a file"
    (let [orders-chan (async/chan)
          ingester-chan (ingest-orders/ingest!
                         {:orders-file "test/orders.json"
                          :ingest-rate 1000
                          :orders-chan orders-chan})]
      (is (= example-orders (repeatedly 2 #(async/<!! orders-chan))))
      (is (nil? (async/poll! orders-chan)))
      (is (nil? (async/<!! ingester-chan)))))
  (testing "Ingest orders from supplied data"
    (let [orders-chan (async/chan)
          ingester-chan (ingest-orders/ingest!
                         {:orders example-orders
                          :ingest-rate 1000
                          :orders-chan orders-chan})]
      (is (= example-orders (repeatedly 2 #(async/<!! orders-chan))))
      (is (nil? (async/poll! orders-chan)))
      (is (nil? (async/<!! ingester-chan))))))
