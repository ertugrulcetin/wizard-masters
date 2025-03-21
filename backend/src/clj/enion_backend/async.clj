(ns enion-backend.async
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [enion-backend.utils :refer [dev?]]
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [msgpack.clojure-extensions]
    [msgpack.core :as msg]
    [promesa.core :as p]
    [sentry-clj.core :as sentry]
    [weavejester.dependency :as dep]))

(defonce procedures (atom {}))
(defonce procedure-map (atom {}))
(defonce deps (atom (dep/graph)))
(defonce validation-fn (atom nil))

(defn dependencies [id]
  (if-let [deps (get (:dependencies @deps) id)]
    (cons id (map dependencies deps))
    [id]))

(defn dependents [id]
  (if-let [depends (get (:dependents @deps) id)]
    (cons id (map dependents depends))
    [id]))

(defn register-validation-fn!
  "Validation function (optional) can only return true, false and a string.
   If it returns string which indicates validation failed with an error message.

  (register-validation-fn!
    (fn [schema data]
      (or (malli/validate schema data)
          (malli/humanize (malli/explain schema data)))))"
  [f]
  (reset! validation-fn f))

(defn dispatch
  "Dispatches socket, req and data to procedure.
  (dispatch ::get-employee-list-by-company {:req req
                                            :socket socket
                                            :data \"Company A\"
                                            :send-fn (fn [socket result]
                                                      (s/put! socket result))})"
  [pro-name payload]
  {:pre [((every-pred :req :socket :send-fn) payload)]}
  (let [{:keys [stream async-flow] :as pro} (get @procedure-map pro-name)]
    (when-not pro
      (throw (ex-info (str "Procedure " pro-name " is not defined.") {})))
    (when-not (realized? async-flow)
      @async-flow)
    (s/put! stream payload)))

(defn dispatch-sync
  "Sync version of dispatch function that returns a response. It's a blocking operation.
   It should be used inside reg-pros (when there is a need for calling other reg-pros with different params)."
  [pro-name payload]
  {:pre [(:req payload)]}
  (let [d (d/deferred)]
    (dispatch pro-name (assoc payload ::deliver (fn [_ result]
                                                  (d/success! d result))
                              :socket (fn [])
                              :send-fn (fn [_ _])))
    @d))

(defn dispatch-http-async
  [pro-name payload]
  {:pre [(:req payload)]}
  (let [d (p/deferred)]
    (dispatch pro-name (assoc payload ::deliver (fn [_ result]
                                                  (p/resolve! d {:status 200
                                                                 :body result}))
                              :socket (fn [])
                              :send-fn (fn [_ _])))
    d))

(defn cancel-pro [pro-name]
  (some-> @procedure-map pro-name :stream (s/put! (Exception.))))

(defmacro validate-if-exists [pro-name s param result]
  `(when-let [m# (get-in @procedure-map [~s :data-response-schema-map])]
     (when-not @validation-fn
       (throw (ex-info "You need to register validation fn `(register-validation-fn!)` in order to use data validation!" {})))
     (let [validation-fn# @validation-fn
           data-validation-result# (when (:data m#)
                                     (validation-fn# (:data m#) ~param))
           response-validation-result# (when (:response m#)
                                         (validation-fn# (:response m#) ~result))]
       (when (and (not (nil? data-validation-result#))
                  (not (true? data-validation-result#)))
         (throw (ex-info (str "Data validation failed for " ~pro-name
                              (when (string? data-validation-result#)
                                (str " - " data-validation-result#))) {})))
       (when (and (not (nil? response-validation-result#))
                  (not (true? response-validation-result#)))
         (throw (ex-info (str "Response validation failed for " ~pro-name
                              (when (string? response-validation-result#)
                                (str " - " response-validation-result#))) {}))))))

(defn merge-kw [k]
  (str/replace (str/join "-" (remove nil? [(namespace k) (name k)])) "." "-"))

(defmacro create-async-flow [pro-name]
  (let [topo-sorted-deps (-> (get-in @enion-backend.async/procedure-map [pro-name :topo-sort]))]
    `(let [stream# (get-in @enion-backend.async/procedure-map [~pro-name :stream])]
       (log/debug "Initializing async flow for" ~pro-name)
       (d/loop [~@(mapcat (fn [s#] [(symbol (merge-kw s#)) `(d/deferred)]) topo-sorted-deps)]
         (d/chain
           (s/take! stream# ::drained)

           (fn [~'payload]
             (log/debug "Got the payload for" ~pro-name)
             (when (instance? Throwable ~'payload)
               (log/debug "Cancelling async flow...")
               (s/close! stream#)
               (throw ~'payload))

             ;; It should not hit here ever, let's keep logging to be safe just in case
             (when (identical? ::drained ~'payload)
               (log/error "Stream drained - cancelling async flow")
               (s/close! stream#)
               (throw (ex-info "Drained stream" {})))

             ~@(map (fn [s#] `(d/success! ~(symbol (merge-kw s#)) (:data ~'payload))) topo-sorted-deps)
             ~'payload)

           (fn [~'payload]
             (log/debug "Processing async flow for" ~pro-name)
             (d/chain
               (d/let-flow [~@(mapcat
                                (fn [s#]
                                  [(symbol (merge-kw s#))
                                   `(d/future
                                      (d/chain
                                        ~(symbol (merge-kw s#))
                                        (fn [_#]
                                          (let [deps# ~(mapv (comp symbol merge-kw) (get-in @procedure-map [s# :deps]))]
                                            (if (seq deps#)
                                              (conj deps# ~'payload)
                                              ~'payload)))
                                        (fn [params#]
                                          (when-not (or (and (vector? params#) (some #(instance? Throwable %) params#))
                                                        (instance? Throwable params#)
                                                        (nil? params#))
                                            (try
                                              (if-let [f# (get-in @procedure-map [~s# :fn])]
                                                (let [result# ((f#) params#)
                                                      data# (:data ~'payload)]
                                                  (validate-if-exists ~pro-name ~s# data# result#)
                                                  result#)
                                                (throw (ex-info (str "Procedure not defined: " ~s#) {})))
                                              (catch Throwable e#
                                                (sentry/send-event
                                                  {:message (str "Procedure not defined: " ~s#)
                                                   :throwable e#})
                                                e#))))))])
                                topo-sorted-deps)]
                           (log/debug "All procedures are realized -" ~pro-name)
                           (try
                             (let [send-fn# (or (::deliver ~'payload) (:send-fn ~'payload))]
                               (if-let [e# (some #(when (instance? Throwable %) %) ~(mapv (comp symbol merge-kw) topo-sorted-deps))]
                                 (let [err-msg# (.getMessage ^Exception e#)]
                                   (log/error e# "Error occurred for" ~pro-name)
                                   (send-fn# (:socket ~'payload) {:id ~pro-name :error err-msg#}))
                                 (let [result# ~(symbol (merge-kw (last topo-sorted-deps)))]
                                   (log/debug "Sending data for" ~pro-name "Data:" result#)
                                   (send-fn# (:socket ~'payload) {:id ~pro-name :result result#}))))
                             (catch Throwable e#
                               (log/error e# "Something went wrong when sending data back to the client! -" ~pro-name)
                               (sentry/send-event
                                 {:message (str "Something went wrong when sending data back to the client! - " ~pro-name)
                                  :throwable e#}))))))

           (fn [_#]
             (log/debug "Process completed for" ~pro-name)
             (d/recur ~@(map (fn [_#] `(d/deferred)) (range (count topo-sorted-deps))))))))))

(defn start-procedures []
  (doseq [[_ p] @procedures]
    (p)))

(defn reg-pro
  "Defines a procedure with any doc-string or deps added to the procedure map.
   data-&-response-schema-map? defines a map with optional keys :data and :response that contain data or response schema.

   e.g.;
   (reg-pro
    :current-user
    (fn [{:keys [req]}]
     (println \"Request: \" req)
     {:user (get-user-by-username (-> req :query-params (get \"username\")))}))

  (reg-pro
   :get-current-users-favorite-songs
   [:current-user]
   {:data [:map [:category string?]]
    :response [:map [:songs (vector string?)]]}
   (fn [current-user {:keys [req data]}]
     (let [user-id (-> current-user :user :id)
           music-category (:category data)]
       {:songs (get-favorite-songs-by-user-id-and-music-category user-id music-category)})))"
  {:arglists '([name-kw doc-string? deps? data-&-response-schema-map? body])}
  [pro-name & fdecl]
  (let [m (if (string? (first fdecl))
            {:doc (first fdecl)}
            {})
        fdecl (if (string? (first fdecl))
                (next fdecl)
                fdecl)
        m (if (vector? (first fdecl))
            (assoc m :deps (first fdecl))
            m)
        fdecl (if (vector? (first fdecl))
                (next fdecl)
                fdecl)
        m (if (map? (first fdecl))
            (assoc m :data-response-schema-map (first fdecl))
            m)
        pro (fn []
              (when-not (fn? (last fdecl))
                (throw (ex-info "Last argument must be a function" {})))
              (swap! procedure-map update pro-name merge (merge {:fn (bound-fn* (fn [] (last fdecl)))
                                                                 :data-response-schema-map nil} m))
              (reset! deps (dep/graph))
              (doseq [[k v] @procedure-map]
                (swap! deps (fn [deps#]
                              (reduce #(dep/depend %1 k %2) deps# (:deps v)))))
              (doseq [[k _] @procedure-map]
                (let [dependencies# (dependencies k)
                      topo-sort (filter (-> dependencies# flatten set) (dep/topo-sort @deps))
                      topo-sort (if (empty? topo-sort) [k] topo-sort)]
                  (swap! procedure-map assoc-in [k :topo-sort] topo-sort)))
              (doseq [k (distinct (flatten (dependents pro-name)))]
                (let [stream (get-in @procedure-map [k :stream])
                      async-flow (get-in @procedure-map [k :async-flow])
                      new-stream (s/stream)]
                  (when stream
                    (when (realized? async-flow)
                      (s/put! stream (Exception.)))
                    (s/close! stream))
                  (swap! procedure-map (fn [m]
                                         (-> m
                                             (assoc-in [k :stream] new-stream)
                                             (assoc-in [k :async-flow] (delay (eval `(create-async-flow ~k))))))))))
        re-start-pro? (boolean (get @procedure-map pro-name))]
    (swap! procedures assoc pro-name pro)
    (when (and (dev?) re-start-pro?)
      (start-procedures))))

(let [temp {}
      temp-fn (fn [_ _])]
  (defn dispatch-in [pro-name {:keys [id data players world]}]
    (if id
      (when-let [socket (get-in players [id :socket])]
        (try
          (dispatch pro-name {:id id
                              :ping 0
                              :data data
                              :req temp
                              :current-players players
                              :current-world world
                              :socket socket
                              :send-fn (fn [socket {:keys [id result]}]
                                         (when result
                                           (s/put! socket (msg/pack (hash-map id result)))))})
          (catch Exception e
            (log/error e))))
      (try
        (dispatch pro-name {:id nil
                            :ping 0
                            :data data
                            :req temp
                            :current-players players
                            :current-world world
                            :socket temp
                            :send-fn temp-fn})
        (catch Exception e
          (log/error e))))))
