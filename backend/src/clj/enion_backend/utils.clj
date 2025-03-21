(ns enion-backend.utils
  (:require
    [enion-backend.config :refer [env]]))

(defonce id-generator (atom 0))
(defonce party-id-generator (atom 0))

(defn dev? []
  (:dev env))

(defn dissoc-in
  [m [k & ks]]
  (if-not ks
    (dissoc m k)
    (assoc m k (dissoc-in (m k) ks))))

(defn rand-between [min max]
  (+ (Math/floor (* (Math/random) (inc (- max min)))) min))

(defn- assoc-some-transient! [m k v]
  (if (nil? v) m (assoc! m k v)))

(defn assoc-some
  "Associates a key k, with a value v in a map m, if and only if v is not nil."
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (loop [m (assoc-some-transient! (transient m) k v)
          kvs kvs]
     (if (next kvs)
       (recur (assoc-some-transient! m (first kvs) (second kvs)) (nnext kvs))
       (persistent! m)))))

(defn prob? [prob]
  (< (rand) prob))

(defn rand-between-int [min max]
  (int (+ (Math/floor (* (Math/random) (+ (- max min) 1))) min)))
