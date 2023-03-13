(ns kitchen.shelves-test
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :refer [deftest testing is]]
   [kitchen.shelves :as shelves]
   [kitchen.specs]
   [matcher-combinators.test :refer [match?]]
   [tick.core :as tick]))

(defn gen-cooked-order []
  (gen/generate (spec/gen :kitchen/cooked-order)))

(defn fill-temp-shelf [state temp]
  (->> (repeatedly (shelves/shelf-capacity temp) gen-cooked-order)
       (map #(assoc % :temp temp))
       (reduce shelves/put-on-temp-shelf state)))

(defn fill-overflow-shelf [state]
  (->> (repeatedly (shelves/shelf-capacity :overflow) gen-cooked-order)
       (reduce shelves/put-on-overflow-shelf state)))

(def now (tick/instant "2023-03-13T00:00:00Z"))

(deftest cook-and-shelve-test
  (let [id #uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd",
        order {:id id
               :name "Banana Split",
               :temp :frozen,
               :shelf-life 20,
               :decay-rate 0.63}]
    (testing "Empty shelves"
      (is (match? {:shelves {:frozen {id (assoc order :cooked-at now)}}
                   :event [:shelf/placed-on-correct-shelf {:id id}]}
                  (shelves/cook-and-shelve shelves/empty-state
                                           order
                                           now))))

    (testing "Full shelf and space in overflow"
      (let [state (fill-temp-shelf shelves/empty-state :frozen)]
        (is (match? {:shelves {:overflow {id (assoc order :cooked-at now)}}
                     :event [:shelf/placed-on-overflow {:id id}]}
                    (shelves/cook-and-shelve state order now)))))

    (testing "Full shelf and full overflow, but room to replace
              existing from overflow to temp shelf"
      (let [state (-> shelves/empty-state
                      (fill-temp-shelf :frozen)
                      (fill-overflow-shelf)
                      (update-in [:shelves :overflow]
                                 update-vals
                                 #(assoc % :temp :hot)))]
        (is (match? {:shelves {:overflow {id (assoc order :cooked-at now)}
                               :hot #(= 1 (count %))}
                     :event [:shelf/placed-on-overflow-replacing-existing
                             {:id id}]}
                    (shelves/cook-and-shelve state order now)))))

    (testing "Full shelf and full overflow, no room to replace
              existing overflow order"
      (let [state (-> shelves/empty-state
                      (fill-temp-shelf :frozen)
                      (fill-temp-shelf :cold)
                      (fill-temp-shelf :hot)
                      (fill-overflow-shelf))]
        (is (match? {:shelves {:overflow {id (assoc order :cooked-at now)}}
                     :event [:shelf/placed-on-overflow-discarding-existing
                             {:id id}]}
                    (shelves/cook-and-shelve state order now)))))))
