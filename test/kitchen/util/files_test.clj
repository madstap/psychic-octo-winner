(ns kitchen.util.files-test
  (:require
   [clojure.test :refer [deftest is]]
   [kitchen.util.files :as utils.fs]))

(deftest read-json-file-test
  (is (= [{:id "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd",
           :name "Banana Split",
           :temp "frozen",
           :shelf-life 20,
           :decay-rate 0.63}
          {:id "7f682ade-8375-4ef4-aa9b-45440f361268",
           :name "Kebab",
           :temp "hot",
           :shelf-life 200,
           :decay-rate 0.54}]
         (utils.fs/read-json-file "test/orders.json"))))
