(ns kitchen.shelves-test
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.spec.gen.alpha :as gen]
   [clojure.test :refer [deftest testing is]]
   [kitchen.shelves :as shelves]
   [kitchen.specs]
   [matcher-combinators.test :refer [match?]]
   [tick.core :as tick]))

(defn gen-order [temp]
  (-> (gen/generate (spec/gen :kitchen/order))
      (assoc :temp temp)))

(defn gen-cooked-order
  ([]
   (gen/generate (spec/gen :kitchen/cooked-order)))
  ([temp]
   (assoc (gen-cooked-order) :temp temp)))

(defn fill-temp-shelf [state temp]
  (->> (repeatedly (shelves/shelf-capacity temp) #(gen-cooked-order temp))
       (reduce shelves/put-on-temp-shelf state)))

(defn fill-overflow-shelf [state]
  (->> (repeatedly (shelves/shelf-capacity :overflow) gen-cooked-order)
       (reduce shelves/put-on-overflow-shelf state)))

(def now (tick/instant "2023-03-13T00:00:00Z"))

(def id #uuid "a8cfcb76-7f24-4420-a5ba-d46dd77bdffd",)

(def order
  {:id id
   :name "Banana Split",
   :temp :frozen,
   :shelf-life 20,
   :decay-rate 0.63})

(def cooked-order
  (assoc order :cooked-at now))

(deftest cook-and-shelve-test
  (testing "Empty shelves"
    (is (match? {:shelves {:frozen {id cooked-order}}
                 :event [:shelf/placed-on-correct-shelf {:id id}]}
                (shelves/cook-and-shelve shelves/empty-state order now))))

  (testing "Full shelf and space in overflow"
    (let [state (fill-temp-shelf shelves/empty-state :frozen)]
      (is (match? {:shelves {:overflow {id cooked-order}}
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
      (is (match? {:shelves {:overflow {id cooked-order}
                             :hot #(= 1 (count %))}
                   :event [:shelf/placed-on-overflow-replacing-existing
                           {:id id}]}
                  (shelves/cook-and-shelve state order now))))

    (testing "Chooses the overflow order with the emptiest shelf"
      ;; We make a state that has full overflow, full frozen shelf,
      ;; almost full :cold shelf and an empty :hot shelf. There is a
      ;; single :hot order on the overflow shelf. When we add a frozen
      ;; order it should go to the overflow shelf and the hot order
      ;; should be moved to its proper shelf since that is the
      ;; emptiest one.
      (let [cold-shelf-orders (repeatedly (dec (shelves/shelf-capacity :cold))
                                          #(gen-order :cold))
            {id-to-replace :id, :as order-to-replace} (gen-cooked-order :hot)
            overflow-orders (concat (repeatedly 7 #(gen-order :cold))
                                    (repeatedly 7 #(gen-order :frozen))
                                    [order-to-replace])
            state (-> shelves/empty-state
                      (fill-temp-shelf :frozen)
                      (assoc-in [:shelves :overflow]
                                (zipmap (map :id overflow-orders)
                                        overflow-orders)))
            state' (reduce (fn [state order]
                             (shelves/cook-and-shelve state order now))
                           state
                           cold-shelf-orders)]
        (is (match? {:shelves {:hot {id-to-replace order-to-replace}
                               :overflow {id order}}
                     :event [:shelf/placed-on-overflow-replacing-existing
                             {:id id
                              :replaced-overflow-order id-to-replace}]}
                    (shelves/cook-and-shelve state' order now))))))

  (testing "Full shelf and full overflow, no room to replace
              existing overflow order"
    (let [state (-> shelves/empty-state
                    (fill-temp-shelf :frozen)
                    (fill-temp-shelf :cold)
                    (fill-temp-shelf :hot)
                    (fill-overflow-shelf))]
      (is (match? {:shelves {:overflow {id cooked-order}}
                   :event [:shelf/placed-on-overflow-discarding-existing
                           {:id id}]}
                  (shelves/cook-and-shelve state order now))))))

(deftest pickup-test
  (testing "The order is there for pickup"
    (let [state (-> shelves/empty-state
                    (shelves/put-on-temp-shelf cooked-order))]
      (is (= {:shelves shelves/empty-shelves
              :event [:pickup/succeeded {:id id}]}
             (shelves/pickup state order)))))

  (testing "The order has been discarded"
    (is (= {:shelves shelves/empty-shelves
            :event [:pickup/failed {:id id}]}
           (shelves/pickup shelves/empty-state order)))))
