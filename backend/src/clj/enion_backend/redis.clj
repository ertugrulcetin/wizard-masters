(ns enion-backend.redis
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [enion-backend.db :as db]
    [java-time :as jt]
    [mount.core :as mount]
    [taoensso.carmine :as car]))

(mount/defstate ^{:on-reload :noop} my-conn-pool
                :start (car/connection-pool {}))

(def my-conn-spec-1
  {:uri "[YOUR-REDIS-URI]"})

(defn my-wcar-opts []
  {:pool my-conn-pool
   :spec my-conn-spec-1})

(defmacro wcar* [& body]
  `(car/wcar (my-wcar-opts) ~@body))

(defn purge-redis []
  (wcar* (car/flushall)))

(defn setnx
  "Perform Redis SETNX via raw `redis-call`."
  [k v]
  (car/redis-call ["SETNX" (str k) (str v)]))

(defn incr
  "Perform Redis INCR via raw `redis-call`."
  [k]
  (car/redis-call ["INCR" (str k)]))

(def ^:private global-id-key "global:id:counter")

(defn init-global-id-counter
  "Ensure the global-id-key is set to 0 if it doesn't exist."
  []
  (wcar*
    (setnx global-id-key 0)))                               ; Only sets if key doesn't exist

(defn next-id
  "Atomically increments and returns the global ID counter."
  []
  (wcar*
    (incr global-id-key)))

;; -----------------------------------------------------------------------------
;; Leaderboard Key Generation
;; -----------------------------------------------------------------------------

(defn current-date []
  (jt/local-date (jt/zone-id "UTC")))

(defn daily-key
  ([]
   (daily-key (current-date)))
  ([date]
   (format "leaderboard:daily:%s" (jt/format "yyyy-MM-dd" date))))

(defn weekly-key
  ([]
   (weekly-key (current-date)))
  ([date]
   (format "leaderboard:weekly:%s" (jt/format "YYYY-'W'ww" date))))

(defn monthly-key
  ([]
   (monthly-key (current-date)))
  ([date]
   (format "leaderboard:monthly:%s" (jt/format "yyyy-MM" date))))

(defn global-key []
  "leaderboard:alltime")

(defn get-player-rank
  "Retrieve a player's rank (1-based) in the specified leaderboard."
  [leaderboard-key player-id]
  (let [rank (wcar* (car/zrevrank leaderboard-key player-id))]
    (when rank (inc rank))))

(defn get-player-kills
  [leaderboard-key player-id]
  (wcar* (car/zscore leaderboard-key player-id)))

(defn get-player-deaths
  [leaderboard-key player-id]
  (wcar* (car/zscore (str leaderboard-key ":deaths") player-id)))

(defn parse-top-players
  "Parse the flat list returned by ZREVRANGE WITHSCORES into maps."
  [flat-list]
  (->> flat-list
       (partition 2)
       (map (fn [[player-id score]]
              {:player-id player-id
               :kills (Double/parseDouble score)}))
       vec))

(defn get-top-players
  "Retrieve the top N players from the specified leaderboard."
  [leaderboard-key n]
  (let [flat-list (wcar* (car/zrevrange leaderboard-key 0 (dec n) "WITHSCORES"))]
    (parse-top-players flat-list)))

;; -----------------------------------------------------------------------------
;; Player Data Functions
;; -----------------------------------------------------------------------------

(defn set-player-data
  "Set multiple fields in the player's hash."
  [player-id data]
  (let [key (str "player:" player-id)
        args (mapcat (fn [[k v]] [(name k) (str v)]) data)]
    (apply car/hmset key args)))

(defn get-player-data
  "Get all data fields for a player as a map."
  [player-id]
  (let [data (wcar* (car/hgetall (str "player:" player-id)))]
    (when (and data (not (empty? data)))
      (apply hash-map data))))

(defn get-player-data-specific-time
  "Retrieve the player's data (kills, deaths, kdr, rank) for a specific leaderboard key."
  [user-id key]
  (when-let [player-data (get-player-data user-id)]
    (let [kills (get-player-kills key user-id)
          deaths (get-player-deaths key user-id)
          kills (if kills (parse-long kills) 0)
          deaths (if deaths (parse-long deaths) 0)
          kdr (double (/ kills (if (zero? deaths) 1 deaths)))]
      (-> player-data
          walk/keywordize-keys
          (assoc :kills kills
                 :deaths deaths
                 :kdr kdr)))))

(defn get-top-players-with-data
  "Retrieve top N players along with their data."
  [leaderboard-key n]
  (let [top-players (get-top-players leaderboard-key n)]
    (map (fn [player]
           (let [pdata (get-player-data-specific-time (:player-id player) leaderboard-key)]
             (merge player {:data (walk/keywordize-keys pdata)})))
         top-players)))

(defn get-top-players-all-leaderboards
  "Get top N players for daily, weekly, monthly, and global."
  ([n] (get-top-players-all-leaderboards n (current-date)))
  ([n date]
   {:daily (get-top-players (daily-key date) n)
    :weekly (get-top-players (weekly-key date) n)
    :monthly (get-top-players (monthly-key date) n)
    :global (get-top-players (global-key) n)}))

(defn update-player-stats
  "Increment the player's kills and deaths by the given amounts.
   Then recompute KDR and update all leaderboards based on the new kills,
   all in a single wcar call to minimize round trips."
  [player-id kills-inc deaths-inc]
  (let [player-key (str "player:" player-id)
        old-data (wcar* (car/hgetall player-key))
        data-map (if (and old-data (not (empty? old-data)))
                   (apply hash-map old-data)
                   {})
        username (get data-map "username")
        generated-username (when-not username
                             (str "Guest " (wcar* (car/incr global-id-key))))]
    (wcar*
      (when generated-username
        (car/hset player-key "username" generated-username))

      (let [today (current-date)
            d-key-kills (daily-key today)
            w-key-kills (weekly-key today)
            m-key-kills (monthly-key today)
            g-key-kills (global-key)
            d-key-deaths (str d-key-kills ":deaths")
            w-key-deaths (str w-key-kills ":deaths")
            m-key-deaths (str m-key-kills ":deaths")
            g-key-deaths (str g-key-kills ":deaths")]

        (car/zincrby d-key-kills kills-inc player-id)
        (car/zincrby w-key-kills kills-inc player-id)
        (car/zincrby m-key-kills kills-inc player-id)
        (car/zincrby g-key-kills kills-inc player-id)

        (car/zincrby d-key-deaths deaths-inc player-id)
        (car/zincrby w-key-deaths deaths-inc player-id)
        (car/zincrby m-key-deaths deaths-inc player-id)
        (car/zincrby g-key-deaths deaths-inc player-id)))))

(defn- get-leaderboard-data [key]
  (->> (get-top-players-with-data key 50)
       (mapv (fn [data]
               (assoc (:data data) :uid (:player-id data))))))

(defn- get-uids [data]
  (set (map :uid (apply concat (vals data)))))

(defn get-leaderboard []
  (try
    (let [leaderboard (atom {})]
      (swap! leaderboard assoc :daily (get-leaderboard-data (daily-key (current-date))))
      (Thread/sleep 1200)
      (swap! leaderboard assoc :weekly (get-leaderboard-data (weekly-key (current-date))))
      (Thread/sleep 1200)
      (swap! leaderboard assoc :monthly (get-leaderboard-data (monthly-key (current-date))))
      (Thread/sleep 1200)
      (swap! leaderboard assoc :all-time (get-leaderboard-data (global-key)))
      (let [leaderboard @leaderboard
            uids (get-uids leaderboard)
            uid->username-map (into {} (map (fn [uid]
                                              [uid (db/get-username uid)]) uids))
            replace-username (fn [records]
                               (reduce (fn [acc data]
                                         (let [username (get uid->username-map (:uid data))]
                                           (if-not (str/blank? username)
                                             (conj acc (assoc data :username username))
                                             (conj acc data))))
                                       [] records))
            leaderboard (-> leaderboard
                            (update :daily replace-username)
                            (update :weekly replace-username)
                            (update :monthly replace-username)
                            (update :all-time replace-username))]
        leaderboard))
    (catch Exception e
      (log/error e "Getting Leaderboard failed!"))))

(defn get-player-rank-data [user-id key]
  (when-let [data (get-player-data user-id)]
    (when-let [rank (get-player-rank key user-id)]
      (-> data
          walk/keywordize-keys
          (assoc :rank rank :me? true)))))
