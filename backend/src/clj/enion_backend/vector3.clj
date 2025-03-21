(ns enion-backend.vector3)

;; create all functions in enion-backend.vector2 namespace for 3D vectors
(defn v3 [x y z] [x y z])

(defn sub [v1 v2]
  (v3 (- (first v1) (first v2)) (- (second v1) (second v2)) (- (nth v1 2) (nth v2 2))))

(defn normalize [v]
  (let [len (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2) (Math/pow (nth v 2) 2)))]
    (v3 (/ (first v) len) (/ (second v) len) (/ (nth v 2) len))))

(defn scale [v s]
  (v3 (* (first v) s) (* (second v) s) (* (nth v 2) s)))

(defn inc-len [v n]
  (let [len (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2) (Math/pow (nth v 2) 2)))]
    (v3 (* (first v) (+ len n)) (* (second v) (+ len n)) (* (nth v 2) (+ len n)))))

(defn len [v]
  (Math/sqrt (+ (Math/pow (first v) 2) (Math/pow (second v) 2) (Math/pow (nth v 2) 2))))

(defn dist [v1 v2]
  (len (sub v1 v2)))

(comment
  (len (v3 1 1 1))
  (len (normalize (v3 1 1 1)))
  (len (inc-len (v3 1 1 1) 1))
  (len (inc-len (normalize (v3 1 1 1)) 1))
  (normalize (v3 1 1 1)))
