(ns main.api.vec
  (:refer-clojure :exclude [+ - / * inc dec set])
  (:require
    ["@babylonjs/core/Maths/math" :refer [Vector3]]
    [applied-science.js-interop :as j]))

(defn v3
  ([]
   (Vector3.))
  ([n]
   (Vector3. n n n))
  ([x y z]
   (Vector3. x y z)))

(defn ->v3 [x]
  (if (number? x)
    (v3 x)
    x))

(defn +'
  ([]
   (v3))
  ([x]
   (->v3 x))
  ([x y]
   (let [v1 (->v3 x)
         v2 (->v3 y)]
     (j/call v1 :addInPlace v2)))
  ([x y & more]
   (reduce +' (cons x (cons y more)))))

(defn +
  ([]
   (v3))
  ([x]
   (if (number? x) (v3 x) (j/call x :clone)))
  ([x y]
   (+' (j/call x :clone) y))
  ([x y & more]
   (reduce + (cons x (cons y more)))))

(defn -'
  ([x]
   (if (number? x)
     (v3 (cljs.core/- x) (cljs.core/- x) (cljs.core/- x))
     (j/call x :negateInPlace)))
  ([x y]
   (let [v1 (if (number? x) (v3 x) x)
         v2 (if (number? y) (v3 y) y)]
     (j/call v1 :subtractInPlace v2)))
  ([x y & more]
   (reduce -' (cons x (cons y more)))))

(defn -
  ([x]
   (if (number? x)
     (v3 (cljs.core/- x) (cljs.core/- x) (cljs.core/- x))
     (j/call x :negate)))
  ([x y]
   (-' (j/call x :clone) y))
  ([x y & more]
   (reduce -' (cons x (cons y more)))))

(defn *'
  ([]
   (v3 1))
  ([x]
   (->v3 x))
  ([x y]
   (let [v1 (->v3 x)
         v2 (->v3 y)]
     (j/call v1 :scaleInPlace v2)))
  ([x y & more]
   (reduce *' (cons x (cons y more)))))

(defn *
  ([]
   (v3 1))
  ([x]
   (->v3 x))
  ([x y]
   (let [v1 (->v3 x)
         v2 (->v3 y)]
     (*' (j/call x :clone v1) v2)))
  ([x y & more]
   (reduce * (cons x (cons y more)))))

(defn divide'
  ([x]
   (->v3 (cljs.core/divide 1 x)))
  ([x y]
   (let [v1 (->v3 x)
         v2 (->v3 y)]
     (j/call v1 :divideInPlace v2)))
  ([x y & more]
   (reduce divide' (cons x (cons y more)))))

(defn /
  ([x]
   (->v3 (cljs.core/divide 1 x)))
  ([x y]
   (let [v1 (->v3 x)
         v2 (->v3 y)]
     (divide' (j/call v1 :clone) v2)))
  ([x y & more]
   (reduce / (cons x (cons y more)))))

(defn inc [x]
  (+ x 1))

(defn inc' [x]
  (+' x 1))

(defn dec [x]
  (- x 1))

(defn dec' [x]
  (-' x 1))

(defn set
  ([v v2]
   (j/call v :set (j/get v2 :x) (j/get v2 :y) (j/get v2 :z)))
  ([v x y z]
   (j/call v :set x y z)))

(defn sqr-magnitude [v]
  (let [x (j/get v :x)
        y (j/get v :y)
        z (j/get v :z)]
    (cljs.core/+ (cljs.core/* x x) (cljs.core/* y y) (cljs.core/* z z))))

(defn dot [v1 v2]
  (j/call v1 :dot v2))

(defn cross [v1 v2]
  (j/call v1 :cross v2))

(defn normalize [v]
  (j/call v :normalize))

;; todo move to math
(defn clamp [val min-val max-val]
  (-> val
      (max min-val)
      (min max-val)))

(defn angle [from to]
  (let [num (js/Math.sqrt (cljs.core/* (sqr-magnitude from) (sqr-magnitude to)))]
    (if (< num 1.0000000036274937E-15)
      0
      (cljs.core/* (js/Math.acos (clamp (cljs.core/divide (dot from to) num) -1 1)) 57.29578))))


(comment
  (= (v3) (v3))
  (Math/cos)
  (angle (v3 2 3 5) (v3 40 -2 30))

  (js/Math.sqrt (* (sqr-magnitude (v3 2)) (sqr-magnitude (v3 4))))

  )
