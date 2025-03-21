(ns main.rule-engine
  (:require
    [clojure.set :as set]
    [main.ui.events :as events]
    [re-frame.core :refer [dispatch-sync]]))

(defonce db (atom {}))

(def comp-false-then-second
  (comp false? :then second))

(def comp-true-when-value-change
  (comp true? :when-value-change? second))

(defn- get-last-time-run-kw [rule-name]
  (keyword (str (namespace rule-name) "/" (name rule-name) "-last-time-run")))

(defn reg-rule [name opts]
  (let [what (:what opts)
        whats-with-then-true (remove comp-false-then-second what)
        whats-with-when-value-change? (filter comp-true-when-value-change what)
        what-keys (set (map first whats-with-then-true))]
    (swap! db assoc-in [:rules name] (assoc opts
                                            :whats-with-then-true whats-with-then-true
                                            :whats-with-when-value-change? whats-with-when-value-change?
                                            :what-keys what-keys
                                            :last-time-run-kw (get-last-time-run-kw name)))))

(defn reg-anim [name m]
  (swap! db assoc-in [:animations name] m))

(defn remove-rules [& rules]
  (doseq [r rules]
    (swap! db update :rules dissoc r)))

(defn reg-anim-event [name {:keys [anim frame fn]}]
  (swap! db assoc-in [:animations name anim :events frame] fn))

(defn create-session []
  (swap! db assoc :session {}))

(defn query
  ([attr]
   (-> @db :session attr))
  ([attr1 attr2]
   (-> @db :session attr1 attr2))
  ([attr1 attr2 attr3]
   (-> @db :session attr1 attr2 attr3)))

(defn query-all []
  (:session @db))

(defn get-anim-map [anim-map-name]
  (-> @db :animations anim-map-name))

(defn insert
  ([m]
   (swap! db update :session merge m))
  ([k v]
   (let [m {k v}]
     (swap! db update :session merge m))))

(defn insert-with-ui
  ([m]
   (swap! db update :session merge m)
   (dispatch-sync [::events/insert (query-all)]))
  ([k v]
   (let [m {k v}]
     (swap! db update :session merge m)
     (dispatch-sync [::events/insert (query-all)]))))

(defn upsert
  ([k f]
   (swap! db update-in [:session k] f))
  ([k f a]
   (swap! db update-in [:session k] f a)))

(defn key-is-pressed?
  "Checks if key is pressed in the current frame."
  [key]
  (and ((query :keys-pressed) key)
       (not ((query :keys-was-pressed) key))))

(defn key-was-pressed?
  "Checks if key was pressed in the previous frame."
  [key]
  ((query :keys-was-pressed) key))

(defn register-item-creation [pool f]
  (upsert :pool-item-creation #(assoc % pool f)))

(defn- create-pool-item [pool]
  (when-let [f (-> (query :pool-item-creation)
                   (get pool))]
    (f)))

(defn pop-from-pool [pool]
  (let [pool-items (query pool)
        [item & items] (seq pool-items)]
    (insert pool (set items))
    (or item (create-pool-item pool))))

(defn push-to-pool [pool item]
  (when item
    (let [pool-items (query pool)]
      (insert pool (conj pool-items item)))))

(reg-rule
  :ui/update-state
  {:what {:dt {}}
   :run-every 100
   :then (fn [opts]
           (dispatch-sync [::events/insert (:session opts)]))})

(defn fire-rules
  ([k v]
   (fire-rules {k v}))
  ([m]
   (let [prev-db* @db
         db* (insert m)]
     (doseq [[rule-name {:keys [what
                                whats-with-then-true
                                whats-with-when-value-change?
                                what-keys
                                disabled?]}] (:rules db*)
             :when (not disabled?)
             :let [updated-keys (set (keys (select-keys m what-keys)))]]
       (when (and (seq whats-with-then-true)
                  (seq updated-keys)
                  (empty? (set/difference updated-keys what-keys))
                  (every?
                    (fn [[attr opts]]
                      (if (:allow-nil? opts)
                        true
                        (some? (-> db* :session attr))))
                    what)
                  (or (empty? whats-with-when-value-change?)
                      (some (fn [[key _]]
                              (not= (key m) (-> prev-db* :session key)))
                            whats-with-when-value-change?)))
         (let [{:keys [locals then run-every last-time-run-kw] :as rule} (-> db* :rules rule-name)
               session (:session db*)
               prev-session (:session prev-db*)
               params {:locals locals
                       :session session
                       :prev-session prev-session}]
           (if run-every
             (when (>= (- (js/Date.now) (query last-time-run-kw)) run-every)
               (if (:when rule)
                 (when ((:when rule) params)
                   (then params)
                   (insert last-time-run-kw (js/Date.now)))
                 (do
                   (then params)
                   (insert last-time-run-kw (js/Date.now)))))
             (if (:when rule)
               (when ((:when rule) params)
                 (then params))
               (then params)))))))))
