(ns enion-backend.vector2)

;; create 2D vector
(defn vec2 [x y] [x y])

;; create substraction of 2D vectors
(defn sub [v1 v2]
  (vec2 (- (first v1) (first v2)) (- (second v1) (second v2))))

;; create addition of 2D vectors
(defn add [v1 v2]
  (vec2 (+ (first v1) (first v2)) (+ (second v1) (second v2))))

;; create normalization of 2D vector
(defn normalize [v]
  (let [len (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2)))]
    (vec2 (/ (first v) len) (/ (second v) len))))

;; create scale of 2D vector
(defn scale [v s]
  (vec2 (* (first v) s) (* (second v) s)))

;; create function that increases length of 2d vector provided number via parameter
(defn inc-len [v n]
  (let [len (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2)))]
    (vec2 (* (first v) (+ len n)) (* (second v) (+ len n)))))

;; get length of 2D vector
(defn len [v]
  (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2))))

;; create function that returns distance between 2 2D vectors
(defn dist [v1 v2]
  (len (sub v1 v2)))

(comment
  (len (v2 1 1))
 (len (normalize (v2 1 1)))
 (len (inc-len (v2 1 1) 1))
 (len (inc-len (normalize (v2 1 1)) 1))
 (normalize (v2 1 1)))
