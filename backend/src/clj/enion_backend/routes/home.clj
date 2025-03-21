(ns enion-backend.routes.home
  (:require
    [aleph.http :as http]
    [buddy.core.keys :as keys]
    [buddy.sign.jwt :as jwt]
    [cheshire.core :as json]
    [clj-http.client :as cl-http]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [enion-backend.async :as easync :refer [dispatch dispatch-http-async dispatch-in reg-pro]]
    [enion-backend.db :as db]
    [enion-backend.layout :as layout]
    [enion-backend.middleware :as middleware]
    [enion-backend.redis :as redis]
    [enion-backend.routes.arena :as map.arena]
    [enion-backend.routes.ruins :as map.ruins]
    [enion-backend.routes.temple :as map.temple]
    [enion-backend.routes.towers :as map.towers]
    [enion-backend.shop-items :as shop]
    [enion-backend.utils :as utils :refer [dev?]]
    [kezban.core :refer [when-let*]]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [mount.core :as mount :refer [defstate]]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [nano-id.core :as nano]
    [nano-id.random :as rnd]
    [ring.util.response]
    [sentry-clj.core :as sentry]
    [taoensso.carmine :as car])
  (:import
    (java.time
      Instant)
    (java.util.concurrent
      ExecutorService
      Executors
      TimeUnit)))

(def max-number-of-players 8)

(def max-health 1000)

(def death-match-kill-count
  (if (dev?)
    2
    40))

(def send-snapshot-count 20)
(def world-tick-rate (/ 1000 send-snapshot-count))

(def afk-threshold-in-milli-secs (* 1 60 1000))

(def fire-projectile-diameter 12)
(def fire-projectile-range (/ fire-projectile-diameter 2))

(def toxic-projectile-diameter 8)
(def toxic-projectile-range (/ toxic-projectile-diameter 2))

(def super-nova-diameter 20)
(def super-nova-range (/ super-nova-diameter 2))

(def light-strike-diameter 10)
(def light-strike-range (/ light-strike-diameter 2))

(def wind-tornado-diameter 16)
(def wind-tornado-range (/ wind-tornado-diameter 2))

(defonce rooms (atom {}))

(def generated-ids (atom #{}))

(defn- generate-room-id []
  (let [id ((nano/custom "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ" 5 rnd/random-bytes))]
    (if (@generated-ids id)
      (generate-room-id)
      (do
        (swap! generated-ids conj id)
        id))))

(def room-ids (repeatedly 10 generate-room-id))

(defonce players (atom {}))
(defonce world (atom {}))

(defonce damage-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce fire-projectile-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce toxic-projectile-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce rock-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce super-nova-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce rock-wall-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce light-strike-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce light-staff-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce ice-arrow-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce ice-tornado-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce toxic-cloud-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce wind-slash-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce wind-tornado-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))

(defonce collectable-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))
(defonce equip-stream (atom (into {} (map (fn [id s] [id s]) room-ids (repeatedly s/stream)))))

(def toxic-cloud-id-gen (atom 0))

(def queue-stream (s/stream))
(defonce leaderboard-stream (s/stream))

(defonce leaderboard (atom {}))

(defonce cg-public-key (atom nil))

(defn get-queue-count []
  (:pending-puts (.description queue-stream)))

(def usernames
  #{"Azura" "Thorne" "Zephyr" "Vesper" "Nyx" "Solara"
    "Draven" "Kael" "Seraphis" "Riven" "Eryndor" "Varyn"
    "Lyric" "Maelis" "Zarek" "Thalia" "Lyra" "Arcus"
    "Calyx" "Astrid"})

(def maps
  {:map/ruins {:map-data map.ruins/map-data}
   :map/arena {:map-data map.arena/map-data}
   :map/temple {:map-data map.temple/map-data}
   :map/towers {:map-data map.towers/map-data}})

(defn- get-map-data [map]
  (-> maps map :map-data))

(defn- reset-collectables [map]
  (reduce
    (fn [acc [idx m]]
      (assoc acc idx (assoc m :id idx
                            :current-collectable (first (shuffle (:collectables m)))
                            :respawn-duration (utils/rand-between-int 30 60)
                            :active? true)))
    {}
    (map-indexed vector (-> map get-map-data :collectables))))

(defn- init-rooms []
  (reset! rooms
          (->> (map-indexed (fn [idx id]
                              (let [mode (if (odd? idx)
                                           :solo-death-match
                                           :team-death-match)]
                                [id {:id id
                                     :state :waiting
                                     :private? false
                                     :map-selection-count 0
                                     :maps (if (= mode :solo-death-match)
                                             (dissoc maps :map/arena)
                                             maps)
                                     :mode mode}])) room-ids)
               (into {}))))

(defn fetch-public-key
  "Fetch CrazyGames' public key (PEM) from the official URL."
  []
  (try
    (let [response (cl-http/get "https://sdk.crazygames.com/publicKey.json" {:as :json})]
      (reset! cg-public-key (-> response :body :publicKey keys/str->public-key))
      (log/info "Fetched CG public key."))
    (catch Exception e
      (log/error e "Could not fetch CG public key!")
      (Thread/sleep 5000)
      (log/info "Trying again to fetch CG public key...")
      (fetch-public-key))))

(defn verify-token
  "Verifies and decodes the given JWT using the CrazyGames public key.
   Returns a Clojure map with the decoded claims if successful,
   or throws an exception if verification fails."
  [token]
  (jwt/unsign token @cg-public-key {:alg :rs256}))

(defn get-cg-user-data [token]
  (try
    (verify-token token)
    (catch Exception e
      (log/error e))))

(defn now []
  (.toEpochMilli (Instant/now)))

(defn min-to-millis [min]
  (* 1000 60 min))

(defn hour-to-millis [hour]
  (* 1000 60 60 hour))

(defn boost-active? [player-id boost]
  (let [data (get-in @players [player-id :data])]
    (and (boost data) (< (now) (boost data)))))

(defn- create-room [private? mode]
  (let [room-id (generate-room-id)]
    (swap! rooms assoc room-id {:id room-id
                                :private? private?
                                :map-selection-count 0
                                :created-at (now)
                                :maps (if (= mode :solo-death-match)
                                        (dissoc maps :map/arena)
                                        maps)
                                :state :waiting
                                :mode mode})
    (doseq [stream [damage-stream
                    fire-projectile-stream
                    toxic-projectile-stream
                    rock-stream
                    super-nova-stream
                    rock-wall-stream
                    ice-tornado-stream
                    ice-arrow-stream
                    light-staff-stream
                    light-strike-stream
                    wind-slash-stream
                    wind-tornado-stream
                    toxic-cloud-stream
                    collectable-stream
                    equip-stream]]
      (swap! stream assoc room-id (s/stream)))
    room-id))

(def solo-deathmatch-map-change-interval (min-to-millis 15))

(defn dissoc-in
  ([m ks]
   (if-let [[k & ks] (seq ks)]
     (if (seq ks)
       (let [v (dissoc-in (get m k) ks)]
         (if (empty? v)
           (dissoc m k)
           (assoc m k v)))
       (dissoc m k))
     m))
  ([m ks & kss]
   (if-let [[ks' & kss] (seq kss)]
     (recur (dissoc-in m ks) ks' kss)
     (dissoc-in m ks))))

(defn distance
  ([x x1 z z1]
   (try
     (let [dx (- x x1)
           dz (- z z1)]
       (Math/sqrt (+ (* dx dx) (* dz dz))))
     (catch Exception e
       (log/error e "Something went wrong while calculating 2D distance..."))))
  ([x x1 y y1 z z1]
   (try
     (let [dx (- x x1)
           dy (- y y1)
           dz (- z z1)]
       (Math/sqrt (+ (* dx dx) (* dy dy) (* dz dz))))
     (catch Exception e
       (log/error e "Something went wrong while calculating 3D distance...")))))

(defn send! [player-id pro-id result]
  (when-let [socket (and result (get-in @players [player-id :socket]))]
    (when-not (s/closed? socket)
      (s/put! socket (msg/pack (hash-map pro-id result))))))

(defn get-room-by-id [room-id]
  (get @rooms room-id))

(defn get-room-mode [room-id]
  (get-in @rooms [room-id :mode]))

(defn get-prev-room-id-by-player-id [id]
  (get-in @players [id :prev-room-id]))

(defn get-world-by-room-id [room-id]
  (get @world room-id))

(defn get-room-id-by-player-id [id]
  (get-in @players [id :room-id]))

(defn get-players-with-same-room-id [player-id]
  (let [room-id (get-room-id-by-player-id player-id)]
    (into {} (filter (comp #(= room-id (:room-id %)) second) @players))))

(defn- send-who-killed-who [killer-id victim-id]
  (let [players-to-send (get-players-with-same-room-id killer-id)]
    (doseq [[id _] players-to-send]
      (send! id :who-killed-who [killer-id victim-id]))))

(defn get-player-team [player-id]
  (get-in @players [player-id :team]))

(defn get-player-equipped [player-id]
  (get-in @players [player-id :equipped]))

(defn get-players-by-room-id [room-id]
  (into {} (filter (comp #(= room-id (:room-id %)) second) @players)))

(defn get-respawn-position [player-id]
  (let [team (get-player-team player-id)
        room-id (get-room-id-by-player-id player-id)
        game-mode (get-room-mode room-id)
        room (get-room-by-id room-id)
        room-map (:map room)
        map-data (get-map-data room-map)
        respawn-positions (:respawn-positions map-data)
        team-respawn-positions (if (= :red team)
                                 (:red respawn-positions)
                                 (:blue respawn-positions))]
    (if (= game-mode :solo-death-match)
      (-> (concat (:red respawn-positions) (:blue respawn-positions)) shuffle first)
      (-> team-respawn-positions shuffle first))))

(defn- get-stats [id]
  (let [room-id* (get-room-id-by-player-id id)
        {:keys [red-team-kills blue-team-kills]} (get-room-by-id room-id*)
        stats (->> @players
                   (keep (fn [[player-id {:keys [kills room-id]}]]
                           (when (= room-id* room-id)
                             [player-id {:kills kills
                                         :team (get-player-team player-id)}])))
                   (into {}))]
    {:stats stats
     :red-team-kills red-team-kills
     :blue-team-kills blue-team-kills}))

(defn- send-stats [current-player-id]
  (doseq [[id _] (get-players-with-same-room-id current-player-id)]
    (send! id :stats (get-stats id))))

(defn- get-player-id-by-uid [uid]
  (some
    (fn [p]
      (when (= uid (:uid p))
        (:id p)))
    (vals @players)))

(defn- get-player-user-uid [player-id]
  (some
    (fn [p]
      (when (= player-id (:id p))
        (:uid p)))
    (vals @players)))

(defn- get-collectables [room-id]
  (vals (get-in @rooms [room-id :collectables])))

(defn- update-stats-after-death [killer-player-id died-player-id]
  (when-let* [my-team (get-player-team killer-player-id)
              room-id (get-room-id-by-player-id killer-player-id)]
             (swap! players update-in [killer-player-id :kills] (fnil inc 0))
             (when (= :team-death-match (get-room-mode room-id))
               (swap! rooms update-in [room-id (if (= my-team :red)
                                                 :red-team-kills
                                                 :blue-team-kills)] (fnil inc 0))
               (dispatch-in :end-game {}))
             (when (= :solo-death-match (get-room-mode room-id))
               (swap! players assoc-in [died-player-id :kills] 0))
             (send-who-killed-who killer-player-id died-player-id)
             (send-stats killer-player-id)
             ;; TODO optimize here, do it in batch
             (let [killer-uid (get-player-user-uid killer-player-id)
                   death-uid (get-player-user-uid died-player-id)]
               (s/put! leaderboard-stream {:user-id killer-uid
                                           :kill 1})
               (s/put! leaderboard-stream {:user-id death-uid
                                           :death 1})
               (db/add-coins
                 killer-uid
                 (boost-active? killer-player-id :booster_coin)
                 (fn []
                   (send! killer-player-id :coins {:coins (db/get-user-coin killer-uid)}))))))

(defn- update-stats-after-player-fell [current-player-id]
  (when-let* [my-team (get-player-team current-player-id)
              room-id (get-room-id-by-player-id current-player-id)]
             (when (= :team-death-match (get-room-mode room-id))
               (swap! rooms update-in [room-id (if (= my-team :red)
                                                 :blue-team-kills
                                                 :red-team-kills)] (fnil inc 0))
               (dispatch-in :end-game {}))
             (when (= :solo-death-match (get-room-mode room-id))
               (swap! players assoc-in [current-player-id :kills] 0))
             (s/put! leaderboard-stream {:user-id (get-player-user-uid current-player-id)
                                         :death 1})
             (send-stats current-player-id)))

(defn get-world-by-player-id [player-id]
  (get-world-by-room-id (get-room-id-by-player-id player-id)))

(defn add-fire-projectile [player-id pos dir]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @fire-projectile-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos
                      :dir dir}))))

(defn add-toxic-projectile [player-id pos dir]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @toxic-projectile-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos
                      :dir dir}))))

(defn add-rock [player-id pos-s pos-e rot]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @rock-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos-s pos-s
                      :pos-e pos-e
                      :rot rot}))))

(defn add-super-nova [player-id pos]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @super-nova-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos}))))

(defn add-rock-wall [player-id pos rot]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @rock-wall-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos
                      :rot rot}))))

(defn add-light-strike [player-id pos]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @light-strike-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos}))))

(defn add-wind-tornado [player-id pos]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @wind-tornado-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos}))))

(defn add-ice-tornado [player-id pos]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @ice-tornado-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos}))))

(defn add-toxic-cloud [player-id pos toxic-cloud-id]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @toxic-cloud-stream room-id)]
      (s/put! stream {:player-id player-id
                      :pos pos
                      :toxic-cloud-id toxic-cloud-id}))))

(defn add-ice-arrow [player-id enemy-id]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @ice-arrow-stream room-id)]
      (if enemy-id
        (s/put! stream {:player-id player-id
                        :enemy-id enemy-id})
        (s/put! stream {:player-id player-id})))))

(defn add-light-staff [player-id enemy-id point]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @light-staff-stream room-id)]
      (if enemy-id
        (s/put! stream {:player-id player-id
                        :point point
                        :enemy-id enemy-id})
        (s/put! stream {:player-id player-id
                        :point point})))))

(defn add-wind-slash [player-id enemy-id dir]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @wind-slash-stream room-id)]
      (if enemy-id
        (s/put! stream {:player-id player-id
                        :enemy-id enemy-id})
        (s/put! stream {:player-id player-id
                        :dir dir})))))

(defn add-damage-effect [player-id type]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @damage-stream room-id)]
      (s/put! stream {:player-id player-id
                      :type type}))))

(defn add-update-equip [player-id equip]
  (when-let [room-id (get-room-id-by-player-id player-id)]
    (let [stream (get @equip-stream room-id)]
      (s/put! stream {:player-id player-id
                      :equipped equip}))))

(defn take-while-stream [pred stream]
  (loop [result []]
    (let [value @(s/try-take! stream 0)]
      (if (pred value)
        (recur (conj result value))
        result))))

(defn home-page
  [request]
  (layout/render request "index.html"))

(def comp-not-nil (comp not nil?))

(defn notify-players-for-exit [id username other-player-ids]
  (doseq [other-player-id other-player-ids]
    (println "Sending exit notification...")
    (log/info "Sending exit notification, player id: " id)
    (send! other-player-id :player-exit id)))

(defn get-players-with-room-id []
  (into {} (filter (comp :room-id second) @players)))

(defn- all-boosters-active? [player-id]
  (every? #(boost-active? player-id %) [:booster_regen_mana
                                        :booster_defense
                                        :booster_damage
                                        :booster_regen_hp
                                        :booster_coin
                                        :booster_discord
                                        :booster_cooldown
                                        :booster_stun
                                        :booster_root]))

(defn- send-world-snapshots* []
  (try
    (let [players* (get-players-with-room-id)]
      (doseq [[room-id players-world] @world]
        (let [fire-projectiles (take-while-stream comp-not-nil (get @fire-projectile-stream room-id))
              toxic-projectiles (take-while-stream comp-not-nil (get @toxic-projectile-stream room-id))
              rock-balls (take-while-stream comp-not-nil (get @rock-stream room-id))
              super-novas (take-while-stream comp-not-nil (get @super-nova-stream room-id))
              light-strikes (take-while-stream comp-not-nil (get @light-strike-stream room-id))
              rock-walls (take-while-stream comp-not-nil (get @rock-wall-stream room-id))
              ice-tornados (take-while-stream comp-not-nil (get @ice-tornado-stream room-id))
              toxic-clouds (take-while-stream comp-not-nil (get @toxic-cloud-stream room-id))
              ice-arrows (take-while-stream comp-not-nil (get @ice-arrow-stream room-id))
              light-staffs (take-while-stream comp-not-nil (get @light-staff-stream room-id))
              wind-slashes (take-while-stream comp-not-nil (get @wind-slash-stream room-id))
              wind-tornados (take-while-stream comp-not-nil (get @wind-tornado-stream room-id))
              damage-effects (take-while-stream comp-not-nil (get @damage-stream room-id))
              collectables (take-while-stream comp-not-nil (get @collectable-stream room-id))
              update-equips (take-while-stream comp-not-nil (get @equip-stream room-id))
              world* (into {}
                           (map (fn [world]
                                  (let [[player-id player-data] world]
                                    ;; (println "boost? " (all-boosters-active? player-id))
                                    [player-id (assoc player-data :ping (get-in players* [player-id :ping])
                                                      :boost? (all-boosters-active? player-id))]))
                                players-world))]
          (doseq [[player-id _] world*]
            (let [fire-projectiles (remove #(= player-id (:player-id %)) fire-projectiles)
                  toxic-projectiles (remove #(= player-id (:player-id %)) toxic-projectiles)
                  rock-walls (remove #(= player-id (:player-id %)) rock-walls)
                  rock-balls (remove #(= player-id (:player-id %)) rock-balls)
                  super-novas (remove #(= player-id (:player-id %)) super-novas)
                  light-strikes (remove #(= player-id (:player-id %)) light-strikes)
                  ice-tornados (remove #(= player-id (:player-id %)) ice-tornados)
                  toxic-clouds (remove #(= player-id (:player-id %)) toxic-clouds)
                  wind-tornados (remove #(= player-id (:player-id %)) wind-tornados)
                  ice-arrows (remove #(= player-id (:player-id %)) ice-arrows)
                  wind-slashes (remove #(= player-id (:player-id %)) wind-slashes)
                  update-equips (remove #(= player-id (:player-id %)) update-equips)
                  world* (if (seq rock-walls)
                           (assoc world* :rock-walls rock-walls)
                           world*)
                  world* (if (seq fire-projectiles)
                           (assoc world* :fire-projectiles fire-projectiles)
                           world*)
                  world* (if (seq toxic-projectiles)
                           (assoc world* :toxic-projectiles toxic-projectiles)
                           world*)
                  world* (if (seq rock-balls)
                           (assoc world* :rock-balls rock-balls)
                           world*)
                  world* (if (seq super-novas)
                           (assoc world* :super-novas super-novas)
                           world*)
                  world* (if (seq light-strikes)
                           (assoc world* :light-strikes light-strikes)
                           world*)
                  world* (if (seq ice-tornados)
                           (assoc world* :ice-tornados ice-tornados)
                           world*)
                  world* (if (seq toxic-clouds)
                           (assoc world* :toxic-clouds toxic-clouds)
                           world*)
                  world* (if (seq ice-arrows)
                           (assoc world* :ice-arrows ice-arrows)
                           world*)
                  world* (if (seq wind-slashes)
                           (assoc world* :wind-slashes wind-slashes)
                           world*)
                  world* (if (seq light-staffs)
                           (assoc world* :light-staffs light-staffs)
                           world*)
                  world* (if (seq damage-effects)
                           (assoc world* :damage-effects damage-effects)
                           world*)
                  world* (if (seq wind-tornados)
                           (assoc world* :wind-tornados wind-tornados)
                           world*)
                  world* (if (seq collectables)
                           (assoc world* :collectables collectables)
                           world*)
                  world* (if (seq update-equips)
                           (assoc world* :update-equips update-equips)
                           world*)
                  world* (assoc world* :server-time (now))]
              (send! player-id :world-snapshot world*))))))
    (catch Exception e
      (log/error e "Something went wrong while sending world snapshots..." @world @players))))

(defmacro create-single-thread-executor [millisecs f]
  `(doto (Executors/newSingleThreadScheduledExecutor)
     (.scheduleAtFixedRate (if (dev?) (var ~f) ~f) 0 ~millisecs TimeUnit/MILLISECONDS)))

(defn- send-world-snapshots []
  (create-single-thread-executor world-tick-rate send-world-snapshots*))

(defn- print-world-players* []
  (println "Players: " @players)
  (println "World: " @world))

(defn print-world-players []
  (create-single-thread-executor 30000 print-world-players*))

(defn- print-queue-stream-manager* []
  (println "Queue stream state: " queue-stream))

(defn print-queue-stream-manager []
  (create-single-thread-executor 5000 print-queue-stream-manager*))

(defn- check-afk-and-disconnected-players* []
  (try
    (doseq [[player-id {:keys [socket room-id]}] @players]
      (when (and socket (s/closed? socket))
        (log/info "Clearing disconnected player")
        (swap! players dissoc player-id)
        (when room-id
          (log/info "Clearing disconnected player from room")
          (swap! world dissoc-in [room-id player-id]))))
    (catch Exception e
      (log/error e "Couldn't clear disconnected player!")))

  (doseq [[_ player] @players
          :let [last-activity (get-in player [:last-time :activity])]
          :when last-activity]
    (try
      (when (>= (- (now) last-activity) afk-threshold-in-milli-secs)
        (println "Kicking AFK player... player id: " (:id player))
        (s/close! (:socket player)))
      (catch Exception e
        (log/error e "Couldn't kick AFK player!")))))

(defn- check-afk-and-disconnected-players []
  (when-not (dev?)
    (create-single-thread-executor 1000 check-afk-and-disconnected-players*)))

(defn- add-hp [player-id hp]
  (when-let* [room-id (get-room-id-by-player-id player-id)
              health (get-in @world [room-id player-id :health])
              _ (> health 0)]
             (swap! world update-in [room-id player-id :health] #(min max-health (+ % hp)))))

(defn- restore-hp-&-mp-for-players-out-of-combat* []
  (try
    (let [players (get-players-with-room-id)]
      (doseq [[player-id] players
              :let [room-id (get-room-id-by-player-id player-id)
                    last-damage-time (get-in players [player-id :last-time :damage])
                    health (get-in (get-world-by-player-id player-id) [player-id :health])]
              :when (and room-id health last-damage-time (> health 0))]
        (when (> (- (now) last-damage-time) 5000)
          (add-hp player-id (if (boost-active? player-id :booster_regen_hp)
                              50
                              25)))))
    (catch Exception e
      (log/error e "Couldn't restore hp & mp for players out of combat"))))

(defn- restore-hp-&-mp-for-players-out-of-combat []
  (create-single-thread-executor 1000 restore-hp-&-mp-for-players-out-of-combat*))

(defn- respawn-collectables* []
  (try
    (let [rooms* (->> @rooms
                      (filter (fn [[_ {:keys [state]}]]
                                (= state :started))))]
      (doseq [[room-id {:keys [collectables]}] rooms*
              :let [stream (get @collectable-stream room-id)]]
        (doseq [[collectable-id {:keys [active? last-time-collected respawn-duration collectables]}] collectables
                :when (and (not active?)
                           last-time-collected
                           (>= (- (now) last-time-collected) (* respawn-duration 1000)))
                :let [new-collectable (first (shuffle collectables))]]
          (swap! rooms update-in [room-id :collectables collectable-id] merge
                 {:active? true
                  :current-collectable new-collectable})
          (s/put! stream {:collectable-id collectable-id
                          :current-collectable new-collectable
                          :active? true}))))
    (catch Exception e
      (log/error e "Respawn collectable failed"))))

(defn- respawn-collectables []
  (create-single-thread-executor 1000 respawn-collectables*))

(defn- check-players-fell-manager* []
  (try
    (doseq [[_ players-world] @world]
      (doseq [[player-id player-data] players-world]
        (let [room-id (get-room-id-by-player-id player-id)
              last-time-respawn (get-in @players [player-id :last-time :respawn])]
          (when (and room-id
                     (@players player-id)
                     (:health player-data)
                     (> (:health player-data) 0)
                     (:focus? player-data)
                     (:py player-data)
                     (< (:py player-data) -10)
                     (or (nil? last-time-respawn)
                         (> last-time-respawn 3000)))
            (swap! world assoc-in [room-id player-id :health] 0)
            (send! player-id :fell {})
            (update-stats-after-player-fell player-id)))))
    (catch Exception e
      (log/error e "Fell manager failed!"))))

(defn check-players-fell-manager []
  (create-single-thread-executor 1000 check-players-fell-manager*))

(defn- process-leaderboard-manager* []
  (try
    (doseq [[user-id {:keys [kill death]}] (reduce
                                             (fn [acc {:keys [user-id kill death]}]
                                               (-> acc
                                                   (update-in [user-id :kill] (fnil + 0) (or kill 0))
                                                   (update-in [user-id :death] (fnil + 0) (or death 0))))
                                             {}
                                             (take-while-stream comp-not-nil leaderboard-stream))]
      (println "kill death: " kill death)
      (redis/update-player-stats user-id kill death))
    (println "Players leaderboard update finished")

    (Thread/sleep 1200)

    (reset! leaderboard (redis/get-leaderboard))

    (catch Exception e
      (log/error e "Redis leaderboard update failed!"))))

(defn- update-last-damage-time [id]
  (when (get @players id)
    (swap! players assoc-in [id :last-time :damage] (now))))

(defn- find-next-map [room-id]
  (let [map-selection-count (get-in @rooms [room-id :map-selection-count])
        maps (get-in @rooms [room-id :maps])
        maps-vec (vec (keys maps))]
    (swap! rooms update-in [room-id :map-selection-count] inc)
    (maps-vec (mod map-selection-count (count maps)))))

(defn process-leaderboard-manager []
  (create-single-thread-executor (* 60 1000 15) process-leaderboard-manager*))

(defn- solo-dm-map-changer-manager* []
  (doseq [[room-id {:keys [mode map-change-time]}] @rooms]
    (when (and (= mode :solo-death-match)
               map-change-time
               (>= (now) map-change-time))
      (let [player-ids (keys (get-players-by-room-id room-id))
            next-map (find-next-map room-id)
            map-change-time (+ (now) solo-deathmatch-map-change-interval)]
        (log/info "Changing map, room id: " room-id " - players count: " (count player-ids))
        (swap! rooms (fn [rooms]
                       (-> rooms
                           (assoc-in [room-id :map] next-map)
                           (assoc-in [room-id :map-change-time] map-change-time)
                           (assoc-in [room-id :collectables] (reset-collectables next-map)))))
        (doseq [player-id player-ids]
          (send! player-id :solo-death-match-map-change {:map next-map
                                                         :map-change-time map-change-time
                                                         :collectables (get-collectables room-id)
                                                         :respawn-pos (get-respawn-position player-id)}))))))

(defn solo-dm-map-changer-manager []
  (create-single-thread-executor 1000 solo-dm-map-changer-manager*))

(defn- remove-from-toxic-cloud-damage-over-time [room-id toxic-cloud-id player-id]
  (swap! rooms dissoc-in [room-id :damage-over-time :toxic-cloud toxic-cloud-id :player-ids player-id]))

(reg-pro
  :toxic-cloud-damage-over-time
  (fn [_]
    (try
      (doseq [[room-id {:keys [damage-over-time]}] @rooms]
        (when damage-over-time
          (doseq [[toxic-cloud-id {:keys [created-at
                                          damage-range
                                          times
                                          owner-id
                                          player-ids]}] (:toxic-cloud damage-over-time)]
            (if (> (- (now) created-at) 7500)
              (swap! rooms dissoc-in [room-id :damage-over-time :toxic-cloud toxic-cloud-id])
              (doseq [[player-id {:keys [count]}] player-ids]
                (let [world* (get-world-by-player-id player-id)]
                  (if world*
                    (let [player-data (get world* player-id)
                          {:keys [health focus?]} player-data]
                      (if (seq player-data)
                        (if (or (= health 0)
                                (not focus?)
                                (= times count))
                          (remove-from-toxic-cloud-damage-over-time room-id toxic-cloud-id player-id)
                          (let [[d1 d2] damage-range
                                damage (first (shuffle (range d1 (inc d2))))
                                world (swap! world (fn [world]
                                                     (let [health (max 0 (- (get-in world [room-id player-id :health]) damage))
                                                           died? (= 0 health)
                                                           world (assoc-in world [room-id player-id :health] health)]
                                                       (if died?
                                                         (assoc-in world [room-id player-id :st] "die")
                                                         world))))
                                died? (= 0 (get-in world [room-id player-id :health]))]
                            (swap! rooms update-in [room-id
                                                    :damage-over-time
                                                    :toxic-cloud
                                                    toxic-cloud-id
                                                    :player-ids
                                                    player-id
                                                    :count] (fnil inc 0))
                            (when died?
                              (update-stats-after-death owner-id player-id))
                            (send! player-id :got-toxic-cloud-damage-over-time
                                   {:player-id owner-id
                                    :damage damage
                                    :died? died?})
                            (send! owner-id :toxic-cloud {:damage-and-positions [[player-id damage died?]]})
                            (update-last-damage-time player-id)
                            (add-damage-effect player-id :fire)))
                        (remove-from-toxic-cloud-damage-over-time room-id toxic-cloud-id player-id)))
                    (remove-from-toxic-cloud-damage-over-time room-id toxic-cloud-id player-id))))))))
      (catch Exception e
        (log/error e "Toxic cloud damage over time failed!")))))

;; TODO move to dispatch-in
(defn- check-toxic-cloud-damage-manager* []
  (dispatch-in :toxic-cloud-damage-over-time {}))

(defn check-toxic-cloud-damage-manager []
  (create-single-thread-executor 1500 check-toxic-cloud-damage-manager*))

(defn- shutdown [^ExecutorService ec]
  (.shutdown ec)
  (try
    (when-not (.awaitTermination ec 2 TimeUnit/SECONDS)
      (.shutdownNow ec))
    (catch InterruptedException _
      ;; TODO may no need interrupt fn
      (.. Thread currentThread interrupt)
      (.shutdownNow ec))))

(defn- update-last-activity-time [id]
  (when (get @players id)
    (swap! players assoc-in [id :last-time :activity] (now))))

(defn update-last-combat-time [& player-ids]
  (doseq [id player-ids]
    (when (get @players id)
      (swap! players assoc-in [id :last-time :combat] (now)))))

(def selected-keys-of-set-state [:px :py :pz :rx :ry :rz :st :focus? :light-staff-hits])

(reg-pro
  :set-state
  (fn [{:keys [id data]}]
    (let [room-id (get-room-id-by-player-id id)
          world* (get-world-by-room-id room-id)]
      (when (and room-id
                 (@players id)
                 (not= (get-in world* [id :st]) "die")
                 (= :started (:state (get-room-by-id room-id))))
        (let [light-staff-hits (:light-staff-hits data)
              data (dissoc data :light-staff-hits)]
          (when (seq light-staff-hits)
            (dispatch-in :light-staff-hits {:id id
                                            :players @players
                                            :data light-staff-hits}))
          (swap! world update-in [room-id id] merge (select-keys data selected-keys-of-set-state))
          (update-last-activity-time id))))
    nil))

(reg-pro
  :set-game-focus
  (fn [{:keys [id data]}]
    (let [room-id (get-room-id-by-player-id id)
          focus? (:focus? data)]
      (when (and room-id (@players id) (get-world-by-player-id id))
        (swap! world assoc-in [room-id id :focus?] focus?)))
    nil))

(defn get-red-team-players [players]
  (filter #(= :red (:team %)) (vals players)))

(defn get-blue-team-players [players]
  (filter #(= :blue (:team %)) (vals players)))

(defn all-rooms-full? [mode]
  (if-let [counts (seq (for [room-id (keys (filter #(and (= mode (:mode %)) (not (:private? %))) @rooms))]
                         (count (get-players-by-room-id room-id))))]
    (every? #(= % max-number-of-players) counts)
    false))

(defn create-username [room-id]
  (let [players (get-players-by-room-id room-id)
        username (first (shuffle (set/difference usernames (set (map :username (vals players))))))]
    (when-not username
      (log/error "No username available!, room-id: " room-id, "players count: " (count players)))
    username))

(defn- get-available-team-type [room-id]
  (let [players (get-players-by-room-id room-id)
        red-team-players (count (get-red-team-players players))
        blue-team-players (count (get-blue-team-players players))]
    (if (> blue-team-players red-team-players)
      :red
      :blue)))

(defn get-player-cg-username [player-id]
  (get-in @players [player-id :cg-username]))

(defn get-player-username [player-id]
  (get-in @players [player-id :username]))

(defn had-username? [player-id]
  (get-in @players [player-id :has-username?]))

(defn get-map-change-time [room-id]
  (get-in @rooms [room-id :map-change-time]))

(defn- join-player-to-room [player-id room-id mode]
  (when (get @players player-id)
    (swap! players (fn [players]
                     (-> players
                         (assoc-in [player-id :mode] mode)
                         (assoc-in [player-id :room-id] room-id)
                         (assoc-in [player-id :prev-room-id] room-id)
                         (assoc-in [player-id :team] (get-available-team-type room-id))
                         (assoc-in [player-id :username] (or (get-player-username player-id)
                                                             (get-player-cg-username player-id)
                                                             (create-username room-id))))))))

(defn get-player-pos [player-id]
  (let [room-id (get-room-id-by-player-id player-id)
        world* (get-world-by-room-id room-id)
        px (get-in world* [player-id :px])
        py (get-in world* [player-id :py])
        pz (get-in world* [player-id :pz])]
    [px py pz]))

(defn- get-world-for-the-first-time [room-id]
  (reduce-kv
    (fn [m k v]
      (assoc m k (merge v {:username (get-player-username k)
                           :team (get-player-team k)
                           :equipped (get-player-equipped k)})))
    {} (get-world-by-room-id room-id)))

(defn- get-map-of-room [room-id]
  (get-in @rooms [room-id :map]))

(defn position-player [player-id room-id]
  (when (nil? room-id)
    (log/error "Position-player - Room id is nil for player id: " player-id))
  (when (@players player-id)
    (let [[x y z] (get-respawn-position player-id)]
      (swap! world (fn [world]
                     (-> world
                         (assoc-in [room-id player-id :px] x)
                         (assoc-in [room-id player-id :py] y)
                         (assoc-in [room-id player-id :pz] z)
                         (assoc-in [room-id player-id :rx] 0)
                         (assoc-in [room-id player-id :ry] Math/PI)
                         (assoc-in [room-id player-id :rz] Math/PI)
                         (assoc-in [room-id player-id :health] max-health)))))))

(defn- position-players-before-game-start [room-id]
  (doseq [[player-id _] (get-players-by-room-id room-id)]
    (position-player player-id room-id)))

(defn- notify-other-players-for-new-join [room-id player-id]
  (doseq [[existing-player-id _] (get-players-by-room-id room-id)
          :when (not= existing-player-id player-id)]
    (send! existing-player-id :new-player-join {:id player-id
                                                :pos (get-respawn-position player-id)
                                                :equipped (get-player-equipped player-id)
                                                :health max-health
                                                :username (get-player-username player-id)
                                                :team (get-player-team player-id)})))

(defn- add-players-with-requested-room-to-games-started []
  (let [players-in-queue (take-while-stream comp-not-nil queue-stream)
        players-with-requests (filter (fn [{:keys [requested-room-id]}]
                                        requested-room-id) players-in-queue)
        players-without-requests (remove (fn [{:keys [requested-room-id]}]
                                           requested-room-id) players-in-queue)]
    (doseq [{:keys [player-id] :as p} players-without-requests
            :when (@players player-id)]
      (s/put! queue-stream p))
    (doseq [{:keys [player-id requested-room-id]} players-with-requests
            :when (@players player-id)]
      (let [available-rooms (->> @rooms
                                 (filter (fn [[_ {:keys [state]}]]
                                           (= state :started)))
                                 (keep (fn [[room-id _]]
                                         (let [remaining-seats (- max-number-of-players (count (get-players-by-room-id room-id)))]
                                           (when (> remaining-seats 0)
                                             room-id))))
                                 set)]
        (if (available-rooms requested-room-id)
          (let [mode (get-room-mode requested-room-id)]
            (join-player-to-room player-id requested-room-id mode)
            (position-player player-id requested-room-id)
            (send! player-id :start-game {:room-id requested-room-id
                                          :mode mode
                                          :map (get-map-of-room requested-room-id)
                                          :map-change-time (get-map-change-time requested-room-id)
                                          :world (get-world-for-the-first-time requested-room-id)
                                          :collectables (get-collectables requested-room-id)
                                          :username (get-player-username player-id)
                                          :team (get-player-team player-id)
                                          :pos (get-player-pos player-id)})
            (notify-other-players-for-new-join requested-room-id player-id))
          (let [room (get-room-by-id requested-room-id)]
            (send! player-id :join-game-fail {:reason (cond
                                                        (nil? room)
                                                        :no-room

                                                        (= :waiting (:state room))
                                                        :not-available

                                                        :else :full)
                                              :room-id requested-room-id})))))))

(defn get-room-id-and-remaining-seats [{:keys [state private? mode]}]
  (->> @rooms
       (filter (fn [[_ room-data]]
                 (and (= state (:state room-data))
                      (= private? (:private? room-data))
                      (= mode (:mode room-data)))))
       (keep (fn [[room-id _]]
               (let [remaining-seats (- max-number-of-players (count (get-players-by-room-id room-id)))]
                 (when (> remaining-seats 0)
                   [room-id remaining-seats]))))
       (sort-by second)))

(defn join-players-to-the-rooms-by-mode-and-state [{:keys [state private? mode on-success]}]
  (doseq [[room-id remaining-seats] (get-room-id-and-remaining-seats {:state state
                                                                      :private? private?
                                                                      :mode mode})]
    (let [player-requests (take-while-stream comp-not-nil queue-stream)
          player-requests-with-mode (filter #(= mode (% :mode)) player-requests)
          player-requests-with-other-mode (remove #(= mode (% :mode)) player-requests)
          n-of-mode-match-requests (take remaining-seats player-requests-with-mode)
          rest-of-mode-match-requests (drop remaining-seats player-requests-with-mode)]
      (doseq [{:keys [player-id] :as r} (concat rest-of-mode-match-requests player-requests-with-other-mode)
              :when (@players player-id)]
        (s/put! queue-stream r))
      (doseq [{:keys [player-id mode]} n-of-mode-match-requests
              :when (@players player-id)]
        (join-player-to-room player-id room-id mode)
        (when on-success
          (on-success room-id player-id mode))))))

(defn- add-players-to-games-started []
  (join-players-to-the-rooms-by-mode-and-state
    {:state :started
     :private? false
     :mode :solo-death-match
     :on-success (fn [room-id player-id mode]
                   (position-player player-id room-id)
                   (send! player-id :start-game {:room-id room-id
                                                 :mode mode
                                                 :map (get-map-of-room room-id)
                                                 :map-change-time (get-map-change-time room-id)
                                                 :world (get-world-for-the-first-time room-id)
                                                 :collectables (get-collectables room-id)
                                                 :username (get-player-username player-id)
                                                 :team :none
                                                 :pos (get-player-pos player-id)})
                   (notify-other-players-for-new-join room-id player-id))})

  (join-players-to-the-rooms-by-mode-and-state
    {:state :started
     :private? false
     :mode :team-death-match
     :on-success (fn [room-id player-id mode]
                   (position-player player-id room-id)
                   (send! player-id :start-game {:room-id room-id
                                                 :mode mode
                                                 :map (get-map-of-room room-id)
                                                 :world (get-world-for-the-first-time room-id)
                                                 :collectables (get-collectables room-id)
                                                 :username (get-player-username player-id)
                                                 :team (get-player-team player-id)
                                                 :pos (get-player-pos player-id)})
                   (notify-other-players-for-new-join room-id player-id))}))

(defn- add-players-to-games-waiting []
  (join-players-to-the-rooms-by-mode-and-state
    {:state :waiting
     :private? false
     :mode :solo-death-match})
  (join-players-to-the-rooms-by-mode-and-state
    {:state :waiting
     :private? false
     :mode :team-death-match}))

(defn- update-room-state-to-start [room-id]
  (let [map (find-next-map room-id)
        game-mode (get-room-mode room-id)
        map-change-time (+ (now) solo-deathmatch-map-change-interval)]
    (when (= game-mode :solo-death-match)
      (swap! rooms #(assoc-in % [room-id :map-change-time] map-change-time)))
    (swap! rooms (fn [rooms]
                   (-> rooms
                       (assoc-in [room-id :state] :started)
                       (assoc-in [room-id :map] map)
                       (assoc-in [room-id :start-time] (now))
                       (assoc-in [room-id :end-time] nil)
                       (assoc-in [room-id :red-team-kills] 0)
                       (assoc-in [room-id :blue-team-kills] 0)
                       (assoc-in [room-id :collectables] (reset-collectables map)))))))

(defn- update-room-state-to-end [room-id]
  (swap! rooms (fn [rooms]
                 (-> rooms
                     (assoc-in [room-id :state] :waiting)
                     (assoc-in [room-id :map] nil)
                     (assoc-in [room-id :red-team-kills] 0)
                     (assoc-in [room-id :blue-team-kills] 0)
                     (assoc-in [room-id :end-time] (now))))))

(defn- notify-players-for-game-start [room-id]
  (let [players (get-players-by-room-id room-id)
        world* (get-world-for-the-first-time room-id)
        collectables (get-collectables room-id)
        mode (get-room-mode room-id)]
    (doseq [[player-id _] players]
      (send! player-id :start-game {:room-id room-id
                                    :mode mode
                                    :map (get-map-of-room room-id)
                                    :map-change-time (get-map-change-time room-id)
                                    :world world*
                                    :collectables collectables
                                    :username (get-player-username player-id)
                                    :team (get-player-team player-id)
                                    :pos (get-player-pos player-id)}))))

(reg-pro
  :start-game
  (fn [_]
    (doseq [room-id (->> @rooms
                         (filter (fn [[room-id {:keys [state]}]]
                                   (let [game-end-time (get-in @rooms [room-id :end-time])]
                                     (and (= state :waiting)
                                          (> (count (get-players-by-room-id room-id)) 0)
                                          (or (nil? game-end-time)
                                              (> (- (now) game-end-time) 5000))))))
                         (map (fn [[room-id _]] room-id)))]
      (log/info "Game starting for room: " room-id)
      (update-room-state-to-start room-id)
      (position-players-before-game-start room-id)
      (notify-players-for-game-start room-id))))

(defn- notify-players-for-game-end [room-id winning-team]
  (let [players-in-room (get-players-by-room-id room-id)]
    (doseq [[player-id _] players-in-room
            :let [team (get-in players-in-room [player-id :team])
                  win? (= team winning-team)]]
      (swap! players #(-> %
                          (assoc-in [player-id :room-id] nil)
                          (assoc-in [player-id :team] nil)
                          (assoc-in [player-id :kills] 0)))
      (send! player-id :end-game {:winning-team winning-team
                                  :win? win?}))
    (swap! world dissoc room-id)))

(reg-pro
  :join-game-queue
  (fn [{:keys [id] {:keys [requested-room-id create-room? mode]} :data}]
    (when (s/closed? queue-stream)
      (log/info "Queue stream closed!"))
    (when (s/drained? queue-stream)
      (log/info "Queue stream drained!"))

    (when (or (s/closed? queue-stream)
              (s/drained? queue-stream))
      (println "Replacing queue stream with new one.")
      (let [player-requests (take-while-stream comp-not-nil queue-stream)]
        (alter-var-root #'queue-stream (constantly (s/stream)))
        (doseq [{:keys [player-id] :as r} player-requests
                :when (@players player-id)]
          (s/put! queue-stream r))))

    (let [mode (or mode :solo-death-match)]
      (if create-room?
        (let [room-id (create-room true mode)]
          (update-room-state-to-start room-id)
          (s/put! queue-stream {:player-id id
                                :requested-room-id room-id
                                :mode mode}))
        (s/put! queue-stream {:player-id id
                              :requested-room-id (if-not (str/blank? requested-room-id)
                                                   (str/upper-case requested-room-id)
                                                   requested-room-id)
                              :mode mode})))
    (update-last-activity-time id)
    {}))

(comment
  (def sss (s/stream 50000))
  queue-stream
  (s/close! queue-stream)
  @(s/take! queue-stream)
  @(s/take! queue-stream)

  (dotimes [_ 100000]
    (s/put! sss {:player-id 12
                 :requested-room-id nil
                 :mode :solo-death-match}))

  (dotimes [_ 100000]
    (println @(s/try-take! sss 0)))

  (s/closed? queue-stream)
  (alter-var-root #'queue-stream (constantly (s/stream)))
  )

(defn- ms->min [ms]
  (format "%.1f" (double (/ ms 60000))))

(defn- log-duration [room-id]
  (let [room (@rooms room-id)
        start-time (:start-time room)
        end-time (:end-time room)]
    (log/info "Game duration:" (ms->min (- end-time start-time)) "minutes for room:" room-id)))

(reg-pro
  :end-game
  (fn [_]
    (doseq [[room-id winning-team] (->> @rooms
                                        (filter (fn [[_ {:keys [state red-team-kills blue-team-kills]}]]
                                                  (and (= state :started)
                                                       (or (>= blue-team-kills death-match-kill-count)
                                                           (>= red-team-kills death-match-kill-count)))))
                                        (map (fn [[room-id {:keys [blue-team-kills]}]]
                                               [room-id (if (>= blue-team-kills death-match-kill-count)
                                                          :blue
                                                          :red)])))]
      (update-room-state-to-end room-id)
      (log-duration room-id)
      (notify-players-for-game-end room-id winning-team))))

(defn- process-game-life-cycle []
  (dispatch-in :start-game {}))

(defonce dequeue-process-started? (atom false))

(defn- create-room-if-needed []
  (when (all-rooms-full? :solo-death-match)
    (create-room false :solo-death-match))
  (when (all-rooms-full? :team-death-match)
    (create-room false :team-death-match)))

(reg-pro
  :process-player-queue
  (fn [_]
    (when-not @dequeue-process-started?
      (reset! dequeue-process-started? true)
      (create-room-if-needed)
      (add-players-with-requested-room-to-games-started)
      (add-players-to-games-started)
      (add-players-to-games-waiting)
      (process-game-life-cycle)
      (reset! dequeue-process-started? false))))

(defn- process-player-queue []
  (dispatch-in :process-player-queue {}))

(defn- queue-manager []
  (create-single-thread-executor 1000 process-player-queue))

(reg-pro
  :stats
  (fn [{:keys [id]}]
    (get-stats id)))

(defn generate-damage [{:keys [distance max-damage area-of-affect-diameter shape-type]}]
  (let [radius (/ area-of-affect-diameter 2)
        reduction-factor (if (and (> distance radius) (= :sphere shape-type))
                           0                                ; No damage if outside the area
                           (- 1 (/ (/ distance 2) radius)))
        random-factor (+ 0.9 (* (rand) 0.2))]               ; Adds a random factor between 0.9 and 1.1
    (long (* max-damage reduction-factor random-factor))))

(defn- within-cylinder? [cylinder-bottom-pos player-pos radius height]
  (let [[x1 y1 z1] cylinder-bottom-pos
        [x2 y2 z2] player-pos
        y2 (+ y2 0.1)
        dx (- x2 x1)
        dz (- z2 z1)
        horizontal-distance (Math/sqrt (+ (* dx dx) (* dz dz)))]
    (and (<= horizontal-distance radius)
         (<= y1 y2)
         (<= y2 (+ y1 height)))))

(defn- register-enemies-for-damage-over-time [created-at owner-id room-id {:keys [id type damage-range times]} player-ids]
  (swap! rooms assoc-in [room-id :damage-over-time type id] {:created-at created-at
                                                             :damage-range damage-range
                                                             :owner-id owner-id
                                                             :times times})
  (doseq [player-id player-ids]
    (swap! rooms assoc-in [room-id :damage-over-time type id :player-ids player-id] {:count 0})))

(defn- enemy? [room-id player-id my-team]
  (if (= :solo-death-match (get-room-mode room-id))
    true
    (not= my-team (get-player-team player-id))))

(defn- get-damage-for-player [damage attacker-id attacked-id]
  (let [damage (if (boost-active? attacker-id :booster_damage)
                 (* damage 1.1)
                 damage)
        damage (if (boost-active? attacked-id :booster_defense)
                 (* damage 0.8)
                 damage)]
    (int damage)))

(defn- apply-range-damage [{:keys [current-player-id
                                   pos
                                   radius
                                   height
                                   diameter
                                   max-damage
                                   damage-pro-id
                                   shape-type
                                   damage-over-time
                                   damage-params]
                            :or {shape-type :sphere}}]
  (when-let [room-id (get-room-id-by-player-id current-player-id)]
    (let [current-player-ids (set (keys (get-players-with-same-room-id current-player-id)))
          my-team (get-player-team current-player-id)
          players-within-range (->> (get-world-by-player-id current-player-id)
                                    (keep
                                      (fn [[player-id player-data]]
                                        (let [[x y z] [(:px player-data) (:py player-data) (:pz player-data)]
                                              [x1 y1 z1] pos
                                              player-distance (distance x x1 y y1 z z1)
                                              within-range? (if (= shape-type :cylinder)
                                                              (within-cylinder? pos [x y z] radius height)
                                                              (<= player-distance radius))]
                                          (when (and (not= player-id current-player-id)
                                                     (current-player-ids player-id)
                                                     (enemy? room-id player-id my-team)
                                                     (> (:health player-data) 0)
                                                     (:focus? player-data)
                                                     within-range?)
                                            [player-id player-distance])))))
          damage-and-positions (for [[player-id distance] players-within-range
                                     :let [damage (generate-damage {:distance distance
                                                                    :max-damage max-damage
                                                                    :area-of-affect-diameter diameter
                                                                    :shape-type shape-type})
                                           damage (get-damage-for-player damage current-player-id player-id)]
                                     :when (> damage 0)]
                                 (let [world (swap! world (fn [world]
                                                            (let [health (max 0 (- (get-in world [room-id player-id :health]) damage))
                                                                  died? (= 0 health)
                                                                  world (assoc-in world [room-id player-id :health] health)]
                                                              (if died?
                                                                (assoc-in world [room-id player-id :st] "die")
                                                                world))))
                                       died? (= 0 (get-in world [room-id player-id :health]))]
                                   (when died?
                                     (update-stats-after-death current-player-id player-id))
                                   (send! player-id damage-pro-id
                                          (merge {:player-id current-player-id
                                                  :damage damage
                                                  :died? died?}
                                                 damage-params))
                                   (update-last-damage-time player-id)
                                   (add-damage-effect player-id (if (= damage-pro-id :got-ice-tornado-hit)
                                                                  :ice
                                                                  :fire))
                                   [player-id damage died?]))]
      (when damage-over-time
        (register-enemies-for-damage-over-time (now)
                                               current-player-id
                                               room-id
                                               damage-over-time
                                               (map first players-within-range)))
      {:damage-and-positions damage-and-positions})))

(defn- generate-ice-arrow-damage-and-mana [{:keys [min-damage max-damage drag-factor]}]
  (let [normalized-drag (/ drag-factor 100)
        base-damage (+ min-damage (* (- max-damage min-damage) normalized-drag))
        randomness (* (- (rand) 0.5) 100)
        final-damage (max 50 (min 1050 (+ base-damage randomness)))
        base-mana 25
        mana-variation (* 75 normalized-drag)
        final-mana (max 20 (min 100 (+ base-mana mana-variation)))]
    {:damage (Math/round (float final-damage))
     :consumed-mana (Math/round (float final-mana))}))

(reg-pro
  :ice-arrow
  (fn [{:keys [id data]}]
    (let [room-id (get-room-id-by-player-id id)
          world* (get-world-by-room-id room-id)
          my-team (get-player-team id)
          player-id-got-hit (:id data)
          arrow-pos (:pos data)]
      (if (and room-id
               player-id-got-hit
               arrow-pos
               (> (get-in world* [player-id-got-hit :health]) 0)
               (get-in world* [player-id-got-hit :focus?])
               (not= (get-in world* [player-id-got-hit :st]) "die")
               (enemy? room-id player-id-got-hit my-team))
        (let [[px py pz] [(get-in world* [player-id-got-hit :px])
                          (get-in world* [player-id-got-hit :py])
                          (get-in world* [player-id-got-hit :pz])]
              [px2 py2 pz2] arrow-pos]
          (if (<= (distance px px2 py py2 pz pz2) 5)
            (let [drag-factor (:drag-factor data)
                  {:keys [damage consumed-mana]} (generate-ice-arrow-damage-and-mana {:min-damage 250
                                                                                      :max-damage 500
                                                                                      :drag-factor drag-factor})
                  damage (get-damage-for-player damage id player-id-got-hit)
                  world (swap! world (fn [world]
                                       (let [health (max 0 (- (get-in world [room-id player-id-got-hit :health]) damage))
                                             died? (= 0 health)
                                             world (assoc-in world [room-id player-id-got-hit :health] health)]
                                         (if died?
                                           (assoc-in world [room-id player-id-got-hit :st] "die")
                                           world))))
                  died? (= 0 (get-in world [room-id player-id-got-hit :health]))]
              (send! player-id-got-hit :got-ice-arrow-hit {:player-id id
                                                           :damage damage
                                                           :consumed-mana consumed-mana
                                                           :died? died?})
              (update-last-damage-time player-id-got-hit)
              (add-ice-arrow id player-id-got-hit)
              (when died?
                (update-stats-after-death id player-id-got-hit))
              {:player-id player-id-got-hit
               :damage damage
               :died? died?})
            (add-ice-arrow id nil)))
        (add-ice-arrow id nil)))))

(defn- generate-ice-light-staff-damage [{:keys [min-damage max-damage dt]}]
  (int (* dt 100 (utils/rand-between min-damage max-damage))))

(reg-pro
  :light-staff-hits
  (fn [{:keys [id data]}]
    (let [player-ids-got-hit data
          room-id (get-room-id-by-player-id id)
          world* (get-world-by-room-id room-id)
          my-team (get-player-team id)
          grouped-hits (group-by :player-id player-ids-got-hit)
          missed-hits (get grouped-hits nil)
          grouped-hits (dissoc grouped-hits nil)]
      (doseq [[player-id hit-data] grouped-hits]
        (let [player-id-got-hit player-id
              point (-> hit-data first :point)]
          (if (and room-id
                   player-id-got-hit
                   (> (get-in world* [player-id-got-hit :health]) 0)
                   (get-in world* [player-id-got-hit :focus?])
                   (not= (get-in world* [player-id-got-hit :st]) "die")
                   (enemy? room-id player-id-got-hit my-team))
            (let [damage (reduce
                           (fn [acc {:keys [dt]}]
                             (+ acc (generate-ice-light-staff-damage {:min-damage 9
                                                                      :max-damage 12
                                                                      :dt dt})))
                           0 hit-data)
                  damage (get-damage-for-player damage id player-id-got-hit)
                  world (swap! world (fn [world]
                                       (let [health (max 0 (- (get-in world [room-id player-id-got-hit :health]) damage))
                                             died? (= 0 health)
                                             world (assoc-in world [room-id player-id-got-hit :health] health)]
                                         (if died?
                                           (assoc-in world [room-id player-id-got-hit :st] "die")
                                           world))))
                  died? (= 0 (get-in world [room-id player-id-got-hit :health]))]
              (send! player-id-got-hit :got-light-staff-hit {:player-id id
                                                             :damage damage
                                                             :died? died?})
              (update-last-damage-time player-id-got-hit)
              (add-light-staff id player-id-got-hit point)
              (when died?
                (update-stats-after-death id player-id-got-hit))
              ;; We have to use send! because this reg-pro called through dispatch-in
              (send! id :light-staff-hits {:player-id player-id-got-hit
                                           :damage damage
                                           :died? died?})))))
      (add-light-staff id nil (-> missed-hits first :point)))))

(reg-pro
  :wind-slash
  (fn [{:keys [id data]}]
    (let [{:keys [player-id dir distance]} data
          room-id (get-room-id-by-player-id id)
          world* (get-world-by-room-id room-id)
          my-team (get-player-team id)
          player-id-got-hit player-id]
      (if (and room-id
               player-id-got-hit
               (> (get-in world* [player-id-got-hit :health]) 0)
               (get-in world* [player-id-got-hit :focus?])
               (not= (get-in world* [player-id-got-hit :st]) "die")
               (enemy? room-id player-id-got-hit my-team))
        (let [damage (utils/rand-between-int 200 250)
              damage (get-damage-for-player damage id player-id-got-hit)
              world (swap! world (fn [world]
                                   (let [health (max 0 (- (get-in world [room-id player-id-got-hit :health]) damage))
                                         died? (= 0 health)
                                         world (assoc-in world [room-id player-id-got-hit :health] health)]
                                     (if died?
                                       (assoc-in world [room-id player-id-got-hit :st] "die")
                                       world))))
              died? (= 0 (get-in world [room-id player-id-got-hit :health]))]
          (send! player-id-got-hit :got-wind-slash-hit {:player-id id
                                                        :dir dir
                                                        :damage damage
                                                        :died? died?})
          (update-last-damage-time player-id-got-hit)
          (add-wind-slash id player-id-got-hit nil)
          (when died?
            (update-stats-after-death id player-id-got-hit))
          {:player-id player-id-got-hit
           :damage damage
           :died? died?})
        (add-wind-slash id nil dir)))))

(reg-pro
  :fire-projectile
  (fn [{:keys [id data]}]
    (try
      (apply-range-damage {:current-player-id id
                           :pos (:pos data)
                           :radius fire-projectile-range
                           :diameter fire-projectile-diameter
                           :max-damage 350
                           :damage-pro-id :got-fire-projectile-hit})
      (catch Exception e
        (log/error e "Fire projectile hit error!")))))

(reg-pro
  :toxic-projectile
  (fn [{:keys [id data]}]
    (try
      (apply-range-damage {:current-player-id id
                           :pos (:pos data)
                           :radius toxic-projectile-range
                           :diameter toxic-projectile-diameter
                           :max-damage 120
                           :damage-pro-id :got-toxic-projectile-hit})
      (catch Exception e
        (log/error e "Toxic projectile hit error!")))))

(reg-pro
  :rock-projectile
  (fn [{:keys [id data]}]
    (try
      (let [current-player-id id
            {:keys [player-id]} data]
        (when-let [room-id (get-room-id-by-player-id current-player-id)]
          (let [current-player-ids (set (keys (get-players-with-same-room-id current-player-id)))
                my-team (get-player-team current-player-id)
                hit? (let [world (get-world-by-player-id current-player-id)
                           current-player-data (get world current-player-id)]
                       (when-let [player-data (get world player-id)]
                         (and (not= player-id current-player-id)
                              (seq current-player-data)
                              (seq player-data)
                              (current-player-ids player-id)
                              (enemy? room-id player-id my-team)
                              (> (:health player-data) 0)
                              (:focus? player-data)
                              (<= (distance (:px player-data) (:px current-player-data)
                                            (:py player-data) (:py current-player-data)
                                            (:pz player-data) (:pz current-player-data)) 30))))
                damage-and-positions (when hit?
                                       (let [min-damage 70
                                             max-damage 90
                                             damage (first (shuffle (range min-damage (inc max-damage))))
                                             damage (get-damage-for-player damage current-player-id player-id)
                                             world (swap! world (fn [world]
                                                                  (let [health (max 0 (- (get-in world [room-id player-id :health]) damage))
                                                                        died? (= 0 health)
                                                                        world (assoc-in world [room-id player-id :health] health)]
                                                                    (if died?
                                                                      (assoc-in world [room-id player-id :st] "die")
                                                                      world))))
                                             died? (= 0 (get-in world [room-id player-id :health]))]
                                         (when died?
                                           (update-stats-after-death current-player-id player-id))
                                         (send! player-id :got-rock-projectile-hit
                                                {:player-id current-player-id
                                                 :damage damage
                                                 :died? died?})
                                         (update-last-damage-time player-id)
                                         (add-damage-effect player-id :fire)
                                         [player-id damage died?]))]
            {:damage-and-positions (if damage-and-positions
                                     [damage-and-positions]
                                     [])})))
      (catch Exception e
        (log/error e "Rock projectile hit error!")))))

(reg-pro
  :rock-wall
  (fn [{:keys [id data]}]
    (try
      (add-rock-wall id (:pos data) (:rot data))
      nil
      (catch Exception e
        (log/error e "Rock wall error!")))))

(reg-pro
  :super-nova
  (fn [{:keys [id data]}]
    (try
      (add-super-nova id (:pos data))
      (apply-range-damage {:current-player-id id
                           :pos (:pos data)
                           :radius super-nova-range
                           :diameter super-nova-diameter
                           :max-damage 600
                           :damage-pro-id :got-super-nova-hit})
      (catch Exception e
        (log/error e "Super nova hit error!")))))

(reg-pro
  :light-strike
  (fn [{:keys [id data]}]
    (try
      (add-light-strike id (:pos data))
      (apply-range-damage {:current-player-id id
                           :pos (:pos data)
                           :shape-type :cylinder
                           :height 8
                           :radius light-strike-range
                           :diameter light-strike-diameter
                           :max-damage 400
                           :damage-pro-id :got-light-strike-hit})
      (catch Exception e
        (log/error e "Light strike hit error!")))))

(reg-pro
  :wind-tornado
  (fn [{:keys [id data]}]
    (try
      (add-wind-tornado id (:pos data))
      (apply-range-damage {:current-player-id id
                           :pos (:pos data)
                           :shape-type :cylinder
                           :height 8
                           :radius wind-tornado-range
                           :diameter wind-tornado-diameter
                           :max-damage 200
                           :damage-pro-id :got-wind-tornado-hit
                           :damage-params {:tornado-pos (:pos data)}})
      (catch Exception e
        (log/error e "Wind tornado hit error!")))))

(reg-pro
  :ice-tornado
  (fn [{:keys [id data]}]
    (try
      (add-ice-tornado id (:pos data))
      (apply-range-damage {:current-player-id id
                           :pos (:pos data)
                           ;; same with super nova
                           :radius super-nova-range
                           :diameter super-nova-diameter
                           :max-damage 350
                           :damage-pro-id :got-ice-tornado-hit})
      (catch Exception e
        (log/error e "Ice tornado hit error!")))))

(reg-pro
  :toxic-cloud
  (fn [{:keys [id data]}]
    (try
      (let [toxic-cloud-id (swap! toxic-cloud-id-gen inc)]
        (add-toxic-cloud id (:pos data) toxic-cloud-id)
        (apply-range-damage {:current-player-id id
                             :pos (:pos data)
                             :radius super-nova-range
                             :diameter super-nova-diameter
                             :max-damage 400
                             :damage-over-time {:id toxic-cloud-id
                                                :type :toxic-cloud
                                                :damage-range [40 60]
                                                :times 4}
                             :damage-pro-id :got-toxic-cloud-hit}))
      (catch Exception e
        (log/error e "Toxic cloud hit error!")))))

(reg-pro
  :entered-toxic-cloud
  (fn [{:keys [id data]}]
    (try
      (let [toxic-cloud-id (:toxic-cloud-id data)
            room-id (get-room-id-by-player-id id)]
        (when (and room-id (seq (get-in @rooms [room-id :damage-over-time :toxic-cloud toxic-cloud-id])))
          (swap! rooms assoc-in [room-id :damage-over-time :toxic-cloud toxic-cloud-id :player-ids id] {:count 0})))
      (catch Exception e
        (log/error e "Entered toxic cloud error!")))))

(reg-pro
  :throw-fire-projectile
  (fn [{:keys [id data]}]
    (try
      (let [pos (:pos data)
            direction (:dir data)]
        (add-fire-projectile id pos direction)
        nil)
      (catch Exception e
        (log/error e "Throw fire projectile error!")))))

(reg-pro
  :throw-toxic-projectile
  (fn [{:keys [id data]}]
    (try
      (doseq [{:keys [pos dir]} data]
        (add-toxic-projectile id pos dir))
      (catch Exception e
        (log/error e "Throw toxic projectile error!")))))

(reg-pro
  :throw-rocks
  (fn [{:keys [id data]}]
    (try
      (doseq [{:keys [pos-s pos-e rot]} data]
        (add-rock id pos-s pos-e rot))
      (catch Exception e
        (log/error e "Throw rock error!")))))

(reg-pro
  :notify-enemies
  (fn [{:keys [id data]}]
    (try
      (let [spell (:spell data)
            pos (:pos data)
            current-player-id id
            radius (case spell
                     :fire super-nova-range
                     :ice super-nova-range
                     :light light-strike-range
                     :wind wind-tornado-range
                     :toxic toxic-projectile-range)]
        (when-let [room-id (get-room-id-by-player-id current-player-id)]
          (let [current-player-ids (set (keys (get-players-with-same-room-id current-player-id)))
                my-team (get-player-team current-player-id)
                players-within-range (->> (get-world-by-player-id current-player-id)
                                          (keep
                                            (fn [[player-id player-data]]
                                              (let [[x y z] [(:px player-data) (:py player-data) (:pz player-data)]
                                                    [x1 y1 z1] pos
                                                    player-distance (distance x x1 y y1 z z1)
                                                    within-range? (<= player-distance radius)]
                                                (when (and (not= player-id current-player-id)
                                                           (current-player-ids player-id)
                                                           (enemy? room-id player-id my-team)
                                                           (> (:health player-data) 0)
                                                           (:focus? player-data)
                                                           within-range?)
                                                  player-id)))))]
            (doseq [player-id players-within-range]
              (send! player-id :notify-damage {:spell spell})))))
      (catch Exception e
        (log/error e "Notify enemies error!")))))

(reg-pro
  :respawn
  (fn [{:keys [id]}]
    (try
      (update-last-activity-time id)
      (let [pos (get-respawn-position id)
            [x y z] pos
            health max-health
            room-id (get-room-id-by-player-id id)]
        (when room-id
          (swap! players assoc-in [id :last-time :respawn] (now))
          (swap! world (fn [world]
                         (-> world
                             (assoc-in [room-id id :px] x)
                             (assoc-in [room-id id :py] y)
                             (assoc-in [room-id id :pz] z)
                             (assoc-in [room-id id :health] health)
                             (assoc-in [room-id id :st] "idle"))))
          {:pos [x y z]}))
      (catch Exception e
        (log/error e "Respawn error!")))))

(reg-pro
  :ping
  (fn [{:keys [id data]}]
    {:timestamp (:timestamp data)
     :online (count (get-world-by-player-id id))}))

(reg-pro
  :ping-server
  (fn [{:keys [data]}]
    {:timestamp (:timestamp data)
     :region (:region data)}))

(reg-pro
  :set-ping
  (fn [{:keys [id data]}]
    (swap! players (fn [players]
                     (-> players
                         (assoc-in [id :ping] (:ping data))
                         (assoc-in [id :last-time-ping-set] (now)))))
    {}))

(reg-pro
  :online
  (fn [_]
    {:count (count @players)}))

(def hp-potion-health 500)

(reg-pro
  :get-collectable
  (fn [{:keys [id data]}]
    (let [collectable-id (:id data)
          room-id (get-room-id-by-player-id id)
          collectable (get-in @rooms [room-id :collectables collectable-id])
          current-collectable (:current-collectable collectable)
          stream (get @collectable-stream room-id)]
      (when (and room-id (:active? collectable))
        (swap! rooms (fn [rooms]
                       (-> rooms
                           (assoc-in [room-id :collectables collectable-id :active?] false)
                           (assoc-in [room-id :collectables collectable-id :last-time-collected] (now)))))
        (s/put! stream {:collectable-id collectable-id
                        :active? false})
        (when (= current-collectable :hp)
          (add-hp id hp-potion-health))
        {:collectable-id collectable-id
         :current-collectable current-collectable}))))

(defn message-sent-too-often? [player-id]
  (let [last-time (get-in @players [player-id :last-time :message-sent])
        now (now)]
    (and last-time (< (- now last-time) 500))))

(reg-pro
  :send-message
  (fn [{:keys [id] {:keys [message]} :data}]
    (log/info "Chat, player id: " id " - message: " message)
    (when-not (message-sent-too-often? id)
      (update-last-activity-time id)
      (when (@players id)
        (swap! players assoc-in [id :last-time :message-sent] (now)))
      (let [players (get-players-with-same-room-id id)]
        (doseq [[player-id] players]
          (send! player-id :got-message {:id id
                                         :message message}))))))

(reg-pro
  :init
  (fn [{:keys [id] {:keys [cg-username requested-room-id]} :data}]
    (try
      (let [pos [0 1 0]
            user-id (get-player-user-uid id)]
        (when (nil? user-id)
          (log/error "User-id is nil, :init process!"))
        (swap! players (fn [players]
                         (-> players
                             (assoc-in [id :cg-username] cg-username)
                             (assoc-in [id :last-time :respawn] (now)))))
        (when (and (not (had-username? id))
                   (not (str/blank? cg-username)))
          (db/update-username user-id cg-username))
        {:id id
         :pos pos})
      (catch Exception e
        (log/error e "Init failed!")))))

(defn ping-too-high? [ping]
  (> ping 5000))

(defn- create-equipped-map [user-data]
  {:head (get-in user-data [:equipped :head])
   :cape (get-in user-data [:equipped :cape])
   :attachment (get-in user-data [:equipped :attachment])})

(defn- update-equip [player-id user-data]
  (when (@players player-id)
    (let [equipped (create-equipped-map user-data)]
      (swap! players assoc-in [player-id :equipped] equipped))))

(defn- auth-user [player-id auth-token cg-auth-token]
  (db/run-async
    {:f (fn []
          (let [{:keys [userId username] :as cg-user-data-from-token} (some-> cg-auth-token get-cg-user-data)
                prev-user-data (when-not (str/blank? auth-token) (db/get-user-data auth-token))
                cg-user-data (when-not (empty? cg-user-data-from-token) (db/get-user-data-by-cg-user-id userId))
                user-data (or cg-user-data prev-user-data)]
            (if (or (empty? user-data)
                    (and cg-user-data-from-token prev-user-data (empty? cg-user-data)))
              (let [new-user (db/create-user userId username prev-user-data (+ (now) (min-to-millis 30)))
                    data (db/get-user-data (:auth new-user))]
                (send! player-id :auth {:auth (:auth new-user)
                                        :data data
                                        :create? true})
                (swap! players assoc-in [player-id :uid] (:uid new-user))
                (when-not (str/blank? username)
                  (swap! players assoc-in [player-id :username] username))
                (swap! players assoc-in [player-id :has-username?] (not (str/blank? username)))
                (swap! players assoc-in [player-id :data] user-data))
              (do
                (when (and (not (str/blank? username))
                           (not (empty? cg-user-data))
                           (not= username (:username cg-user-data)))
                  (db/update-cg-username (:player-id cg-user-data) username))
                (send! player-id :auth {:auth (:auth user-data)
                                        :data user-data})
                (swap! players assoc-in [player-id :uid] (get user-data :player-id))
                (swap! players assoc-in [player-id :username] (or username (get user-data :username)))
                (swap! players assoc-in [player-id :has-username?] (boolean (or username (get user-data :username))))
                (swap! players assoc-in [player-id :data] user-data)
                (db/update-log-in-time (get user-data :player-id))))
            (update-equip player-id user-data)
            (send! player-id :auth-completed? true)))
     :on-failed (fn [e]
                  (log/error e "Could not auth the user!"))}))

(reg-pro
  :purchase
  (fn [{:keys [auth data]}]
    (let [player-data (db/get-user-data auth)
          player-id (:player-id player-data)
          {:keys [coins]} player-data
          item-id* (str "item/" (:item data))
          item-id (keyword item-id*)
          rewarded? (get-in shop/all-items [item-id :rewarded?])
          price (get-in shop/all-items [item-id :price])
          item-type (name (get-in shop/all-items [item-id :type]))]
      (when (empty? player-data)
        (throw (ex-info "Could not authorize, please refresh the page." {})))
      (when (and (not rewarded?)
                 (not (>= coins price)))
        (throw (ex-info "You don't have enough coins!" {})))
      (log/info "Purchasing " item-id " , player id: " player-id)
      ;; TODO when adding Upgrade need to check if player has the item or burnt during the upgrade operation
      (db/purchase player-id price item-id* rewarded?)
      (db/equip-item player-id item-id* item-type)
      (db/get-user-data auth))))

(reg-pro
  :equip
  (fn [{:keys [auth data]}]
    (let [player-data (db/get-user-data auth)
          user-uid (:player-id player-data)
          item-id* (str "item/" (:item data))
          item-id (keyword item-id*)
          item-type (name (get-in shop/all-items [item-id :type]))]
      (when (empty? player-data)
        (throw (ex-info "Could not authorize, please refresh the page." {})))
      (when-not ((:purchases player-data) item-id)
        (throw (ex-info "You don't have the item!" {})))
      ;; TODO when adding Upgrade need to check if player has the item or burnt during the upgrade operation
      (db/equip-item user-uid item-id* item-type)
      (let [data (db/get-user-data auth)]
        (update-equip user-uid data)
        (add-update-equip (get-player-id-by-uid user-uid) (create-equipped-map data))
        data))))

(reg-pro
  :check-room-available
  (fn [{:keys [data]}]
    (let [room-id (:room-id data)
          exists? (some? (get-room-by-id room-id))
          full? (if room-id
                  (= (count (get-players-by-room-id room-id)) max-number-of-players)
                  false)]
      (log/info "Checking room")
      {:room-available? exists?
       :room-full? full?
       :room-id room-id})))

(reg-pro
  :create-room
  (fn [{:keys [data]}]
    (let [private? (:private? data)
          game-mode (keyword (:game-mode data))
          room-id (create-room private? game-mode)]
      (update-room-state-to-start room-id)
      (log/info "Creating room...")
      {:room-id room-id})))

(reg-pro
  :get-booster
  (fn [{:keys [auth data]}]
    (let [player-data (db/get-user-data auth)
          user-uid (:player-id player-data)
          booster-type (keyword (:booster data))
          booster (case booster-type
                    :coin "booster_coin"
                    :regen-mana "booster_regen_mana"
                    :cooldown "booster_cooldown"
                    :regen-hp "booster_regen_hp"
                    :damage "booster_damage"
                    :defense "booster_defense"
                    :stun "booster_stun"
                    :root "booster_root"
                    :discord "booster_discord")
          end-time (+ (now) (if (= :discord booster-type)
                              (hour-to-millis 12000)
                              (min-to-millis 30)))]
      (when (empty? player-data)
        (throw (ex-info "Could not authorize, please refresh the page." {})))
      (try
        (log/info "Getting booster: " booster-type " user id: " user-uid)
        (db/apply-booster user-uid booster end-time)
        (let [user-data (db/get-user-data auth)]
          (when-let [player-id (get-player-id-by-uid user-uid)]
            (swap! players assoc-in [player-id :data] user-data))
          {:server-time (now)
           :user-data user-data})
        (catch Exception e
          (log/error e "Get Booster failed!"))))))

(reg-pro
  :get-server-time
  (fn [_]
    (now)))

(def username-req "* Username must be 2–20 characters and can include only letters (A–Z), numbers (0–9), and underscores. Spaces are not allowed.")
(def password-req "* Password must be at least 8 characters long.")

(reg-pro
  :sign-up
  (fn [{:keys [auth data]}]
    (let [player-data (db/get-user-data auth)
          username (:username data)
          password (:password data)
          update? (:update? data)
          username-regex #"^[A-Za-z0-9_]{2,20}$"
          password-regex #"^.{8,}$"]
      (when (empty? player-data)
        (throw (ex-info "Could not authorize, please refresh the page." {})))
      (when-not (re-find username-regex username)
        (throw (ex-info username-req {})))
      (when-not (re-find password-regex password)
        (throw (ex-info password-req {})))
      (db/sign-up update? player-data username password))))

(reg-pro
  :log-in
  (fn [{:keys [data]}]
    (let [username (:username data)
          password (:password data)
          username-regex #"^[A-Za-z0-9_]{2,20}$"
          password-regex #"^.{8,}$"]
      (when-not (re-find username-regex username)
        (throw (ex-info username-req {})))
      (when-not (re-find password-regex password)
        (throw (ex-info password-req {})))
      (db/log-in username password))))

(reg-pro
  :leaderboard
  (fn [{:keys [auth]}]
    (let [player-data (db/get-user-data auth)
          user-uid (:player-id player-data)
          username (:username player-data)
          leaderboard @leaderboard
          add-me? (fn [records]
                    (reduce (fn [acc data]
                              (let [uid (:uid data)
                                    data (dissoc data :uid)]
                                (if (= user-uid uid)
                                  (conj acc (assoc data :me? true))
                                  (conj acc data))))
                            [] records))
          leaderboard (-> leaderboard
                          (update :daily add-me?)
                          (update :weekly add-me?)
                          (update :monthly add-me?)
                          (update :all-time add-me?))
          in-leader-board? (fn [type] (some :me? (get leaderboard type)))
          add-rank-fn (fn [cycle key]
                        (if-let [record (redis/get-player-rank-data user-uid key)]
                          (conj cycle (if (not (str/blank? username))
                                        (assoc record :username username)
                                        record))
                          cycle))]
      (cond-> leaderboard
        (not (in-leader-board? :daily))
        (update :daily #(add-rank-fn % (redis/daily-key)))

        (not (in-leader-board? :weekly))
        (update :weekly #(add-rank-fn % (redis/weekly-key)))

        (not (in-leader-board? :monthly))
        (update :monthly #(add-rank-fn % (redis/monthly-key)))

        (not (in-leader-board? :all-time))
        (update :all-time #(add-rank-fn % (redis/global-key)))))))

(defn ws-handler
  [req]
  (-> (http/websocket-connection req)
      (d/chain
        (fn [socket]
          (let [now* (now)
                player-id (swap! utils/id-generator inc)
                auth-token (-> req :query-params (get "auth"))
                cg-auth-token (-> req :query-params (get "cg_auth"))]
            (swap! players (fn [players]
                             (-> players
                                 (assoc-in [player-id :id] player-id)
                                 (assoc-in [player-id :socket] socket)
                                 (assoc-in [player-id :last-time :activity] now*)
                                 (assoc-in [player-id :time] now*)
                                 (assoc-in [player-id :auth] auth-token))))
            (log/info "New connection, player id: " player-id)
            (alter-meta! socket assoc :id player-id)
            (s/on-closed socket
                         (fn []
                           (try
                             (let [username (get-in @players [player-id :username])
                                   room-id (or (get-room-id-by-player-id player-id)
                                               (get-prev-room-id-by-player-id player-id))
                                   other-player-ids (keys (dissoc (get-world-by-player-id player-id) player-id))]
                               (swap! players dissoc player-id)
                               (when room-id
                                 (swap! world dissoc-in [room-id player-id]))
                               (future
                                 (Thread/sleep 1000)
                                 (when room-id
                                   (swap! world dissoc-in [room-id player-id]))
                                 (swap! players dissoc player-id)
                                 (notify-players-for-exit player-id username other-player-ids)))
                             (catch Exception e
                               (log/error e "Something went wrong during on close socket.")))))
            (auth-user player-id auth-token cg-auth-token))
          (s/consume
            (fn [payload]
              (try
                (let [id (-> socket meta :id)
                      payload (msg/unpack payload)]
                  (dispatch (:pro payload) {:id id
                                            :data (:data payload)
                                            :ping (get-in @players [id :ping] 0)
                                            :current-players @players
                                            :current-world @world
                                            :req req
                                            :socket socket
                                            :send-fn (fn [socket {:keys [id result]}]
                                                       (when result
                                                         (s/put! socket (msg/pack (hash-map id result)))))}))
                (catch Exception e
                  (log/error e)
                  (sentry/send-event {:message (pr-str {:message (.getMessage e)
                                                        :payload {:data 1}})
                                      :throwable e}))))
            socket))))
  ;; Routing lib expects some sort of HTTP response, so just give it `nil`
  nil)

(defn- player-info [req]
  {:status 200
   :body {:count (count @players)}})

(defn api [{{:keys [pro auth data]} :params :as req}]
  (dispatch-http-async (keyword pro) {:req req
                                      :auth auth
                                      :data data}))

(defn home-routes
  []
  [""
   {:middleware [middleware/wrap-formats
                 ;; wrap-as-async must be last
                 middleware/wrap-as-async]}
   ["/" {:get home-page}]
   ["/stats" {:get player-info}]
   ["/api" {:post api}]
   ["/ws" {:get ws-handler}]])

(defstate register-procedures
  :start (easync/start-procedures))

(defstate ^{:on-reload :noop} snapshot-sender
  :start (send-world-snapshots)
  :stop (shutdown snapshot-sender))

(defstate ^{:on-reload :noop} afk-player-checker
  :start (check-afk-and-disconnected-players)
  :stop (shutdown afk-player-checker))

(defstate ^{:on-reload :noop} restore-hp-&-mp
  :start (restore-hp-&-mp-for-players-out-of-combat)
  :stop (shutdown restore-hp-&-mp))

(defstate ^{:on-reload :noop} game-queue
  :start (queue-manager)
  :stop (shutdown game-queue))

(defstate ^{:on-reload :noop} room-manager
  :start (init-rooms))

(defstate ^{:on-reload :noop} print-world-and-players
  :start (print-world-players)
  :stop (shutdown print-world-and-players))

(defstate ^{:on-reload :noop} print-queue-stream
  :start (print-queue-stream-manager)
  :stop (shutdown print-queue-stream))

(defstate ^{:on-reload :noop} respawn-collectables-checker
  :start (respawn-collectables)
  :stop (shutdown respawn-collectables-checker))

(defstate ^{:on-reload :noop} check-players-fell
  :start (check-players-fell-manager)
  :stop (shutdown check-players-fell))

(defstate ^{:on-reload :noop} check-toxic-cloud-damage
  :start (check-toxic-cloud-damage-manager)
  :stop (shutdown check-toxic-cloud-damage))

(defstate ^{:on-reload :noop} process-leaderboard
  :start (process-leaderboard-manager)
  :stop (shutdown process-leaderboard))

(defstate ^{:on-reload :noop} solo-dm-map-changer
  :start (solo-dm-map-changer-manager)
  :stop (shutdown solo-dm-map-changer))

(defstate ^{:on-reload :noop} process-cg-public-key
  :start (fetch-public-key)
  :stop (shutdown process-cg-public-key))

#_(defstate ^{:on-reload :noop} init-sentry
  :start (sentry/init! "https://[YOUR-SENTRY-URI]"
                       {:traces-sample-rate 1.0}))

(comment
  @leaderboard
  @rooms
  @world
  (count @players)
  (init-rooms)
  (update-room-state-to-start 7)

  (get-room-by-id 4)
  (swap! world assoc-in [7 9 :health] 1000)
  @rooms
  (reset! world {})
  (init-rooms)
  (mount/stop #'snapshot-sender)
  (mount/start #'snawpshot-sender)
  )
