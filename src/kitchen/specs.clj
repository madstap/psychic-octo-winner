(ns kitchen.specs
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.spec.gen.alpha :as gen]
   [tick.core :as tick])
  (:import
   (java.time Instant)))

(defn rate? [n]
  (< 0 n 1))

(def temps #{:hot :cold :frozen})

(defn instant? [x]
  (instance? Instant x))

(spec/def :order/id uuid?)

(spec/def :order/name string?)

(spec/def :order/temp temps)

(spec/def :order/shelf-life nat-int?)

(spec/def :order/decay-rate
  (spec/and number? rate?))

(spec/def :order/cooked-at
  (spec/with-gen instant?
    #(gen/fmap tick/instant (spec/gen inst?))))

(spec/def :kitchen/order
  (spec/keys :req-un [:order/id
                      :order/name
                      :order/temp
                      :order/shelf-life
                      :order/decay-rate]))

(spec/def :kitchen/cooked-order
  (spec/merge :kitchen/order
              (spec/keys :req-un [:order/cooked-at])))
