(ns enion-backend.db
  (:require
    [clojure.string :as str]
    [firestore-clj.core :as f]
    [java-time :as jt]
    [mount.core :as mount]
    [nano-id.core :refer [nano-id]])
  (:import
    (java.nio.charset
      StandardCharsets)
    (java.security
      MessageDigest)
    (java.time
      Instant)
    (java.util
      Collection
      Map)
    (java.util.concurrent
      Executors)))

(mount/defstate ^{:on-reload :noop} db
                :start (f/client-with-creds "resources/keys/firestore.json"))

(defonce ^:private single-thread-executor (Executors/newSingleThreadExecutor))

(defn run-async
  "Runs (f) asynchronously on the single-thread-executor.
   - on-success is called with the result of (f) on success
   - on-failed is called with the Throwable if there's an error"
  [{:keys [f on-success on-failed]}]
  (.submit
    single-thread-executor
    (fn []
      (try
        (let [result (f)]
          (when on-success (on-success result)))
        (catch Throwable t
          (when on-failed (on-failed t)))))))

(defn- gen-uuid []
  (str (random-uuid)))

(defn- create-auth-token []
  (apply str (repeatedly 5 nano-id)))

(defn- current-date []
  (jt/format "yyyy-MM-dd'T'HH:mm" (jt/zoned-date-time (jt/zone-id "UTC"))))

(defn vals-key->str [m]
  (->> m
       (map (fn [[k v]]
              [(name k) (str (namespace v) "/" (name v))]))
       (into {})))

(defn apply-initial-boosters [user-id end-time]
  (f/update! (-> (f/coll db "users")
                 (f/doc user-id))
             (fn [data]
               (reduce
                 (fn [data booster]
                   (assoc data (name booster) end-time))
                 data
                 [:booster_regen_mana
                  :booster_defense
                  :booster_damage]))))

(defn create-user [cg-user-id username prev-user-data end-time]
  (let [auth-token (create-auth-token)
        user (-> (f/coll db "users")
                 (f/add! (merge {"auth" auth-token
                                 "coins" 0
                                 "equipped" {}
                                 "cg_user_id" cg-user-id
                                 "username" username
                                 "created_at" (current-date)}
                                {"equipped" (vals-key->str (:equipped prev-user-data {}))
                                 "created_at" (:created_at prev-user-data (current-date))
                                 "coins" (:coins prev-user-data 0)})))
        user-id (.getId user)]
    (apply-initial-boosters user-id end-time)
    {:auth auth-token
     :uid user-id}))

(defn get-user-purchases [user-id]
  (-> (f/coll db "purchases")
      (f/filter= "owner_id" user-id)
      f/pull
      vals
      (#(map (fn [p] (keyword (get p "item"))) %))
      set))

(defn java->clj [x]
  (cond
    (instance? Map x)
    (->> x
         (map (fn [[k v]] [(java->clj k) (java->clj v)]))
         (into {}))

    (instance? Collection x)
    (map java->clj x)

    :else
    x))

(defn- get-user-data* [user-data]
  (let [player-id (ffirst user-data)]
    (when player-id
      (-> (into {} user-data)
          first
          second
          (assoc :purchases (get-user-purchases player-id)
                 :player-id player-id)
          (clojure.walk/keywordize-keys)
          (update :equipped (fn [e]
                              (into {} (map (fn [[k v]]
                                              [(keyword k) (keyword v)]) e))))))))

(defn get-user-data [auth-token]
  (when-let [user-data (-> (f/coll db "users")
                           (f/filter= "auth" auth-token)
                           f/pull
                           java->clj)]
    (get-user-data* user-data)))

(defn get-user-data-by-cg-user-id [cg-user-id]
  (when-let [user-data (-> (f/coll db "users")
                           (f/filter= "cg_user_id" cg-user-id)
                           f/pull
                           java->clj)]
    (get-user-data* user-data)))

(defn get-user-coin [user-id]
  (-> (f/coll db "users")
      (f/doc user-id)
      f/pull
      java->clj
      clojure.walk/keywordize-keys
      :coins))

(defn update-username [user-id username]
  (f/update! (-> (f/coll db "users")
                 (f/doc user-id))
             #(assoc % "username" username)))

(defn get-username [user-id]
  (-> (f/coll db "users")
      (f/doc user-id)
      f/pull
      (get "username")))

(defn purchase [user-id price item-id rewarded?]
  (when-not rewarded?
    (f/update! (-> (f/coll db "users")
                   (f/doc user-id))
               #(update % "coins" - price)))
  (-> (f/coll db "purchases")
      (f/add! {"owner_id" user-id
               "item" item-id
               "level" 1
               "date" (current-date)})))

(defn apply-booster [user-id booster-db-name end-time]
  (f/update! (-> (f/coll db "users")
                 (f/doc user-id))
             #(assoc % booster-db-name end-time)))

(defn add-coins [user-id boost? on-success]
  (run-async
    {:f (fn []
          (f/update! (-> (f/coll db "users")
                         (f/doc user-id))
                     #(update % "coins" + (if boost?
                                            20
                                            10))))
     :on-success on-success}))

(defn equip-item [user-id new-item-id type]
  (-> (f/doc db (str "users/" user-id))
      (f/assoc! (str "equipped." type) new-item-id)))

(defn sha-256 [original-string]
  (let [digest (MessageDigest/getInstance "SHA-256")
        hashed-bytes (.digest digest (.getBytes original-string StandardCharsets/UTF_8))]
    (format "%064x" (BigInteger. 1 hashed-bytes))))

(defn check-username-exists [username]
  (let [username-lower-case (str/lower-case username)
        data (-> (f/coll db "users")
                 (f/filter= "username_lower_case" username-lower-case)
                 f/pull)]
    (when (seq data)
      (throw (ex-info "Username exists!" {})))))

(defn sign-up [update? player-data username password]
  (let [user-id (:player-id player-data)
        current-username (:username player-data)
        username-lower-case (str/lower-case username)
        password-hash (sha-256 password)
        new-auth-token (create-auth-token)]
    (when-not (and update?
                   current-username
                   username
                   (= (str/lower-case current-username) username-lower-case))
      (check-username-exists username))
    (f/update! (-> (f/coll db "users")
                   (f/doc user-id))
               #(assoc % "username" username
                       "username_lower_case" username-lower-case
                       "password" password-hash
                       "account" true
                       "auth" new-auth-token))
    (get-user-data new-auth-token)))

(defn log-in [username password]
  (let [username-lower-case (str/lower-case username)
        password-hash (sha-256 password)
        new-auth-token (create-auth-token)
        user-data (-> (f/coll db "users")
                      (f/filter= {"username_lower_case" username-lower-case
                                  "password" password-hash})
                      f/pull)
        user-id (ffirst user-data)]
    (when (empty? user-data)
      (throw (ex-info "Log-in failed. Please check your username and password, then try again." {})))
    (f/update! (-> (f/coll db "users")
                   (f/doc user-id))
               #(assoc % "auth" new-auth-token
                       "updated_at" (current-date)))
    (get-user-data new-auth-token)))

(defn update-log-in-time [user-id]
  (f/update! (-> (f/coll db "users")
                 (f/doc user-id))
             #(-> %
                  (assoc "updated_at" (current-date))
                  (update "number-of-plays" (fnil inc 0)))))

(defn update-cg-username [user-id username]
  (f/update! (-> (f/coll db "users")
                 (f/doc user-id))
             #(-> %
                  (assoc "username" username
                         "username_lower_case" (str/lower-case username)))))
