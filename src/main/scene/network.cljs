(ns main.scene.network
  (:require
    [ajax.core :as ajax]
    [ajax.xhrio]
    [applied-science.js-interop :as j]
    [clojure.string :as str]
    [goog.net.ErrorCode :as errors]
    [main.ads :as ads]
    [main.api.sound :as api.sound]
    [main.config :as config]
    [main.rule-engine :as re]
    [main.scene.utils :as scene.utils]
    [main.utils :as utils]
    [msgpack-cljs.core :as msg])
  (:import
    (goog.net
      XhrIo)))

(defonce socket (atom nil))
(defonce open? (atom false))

(def servers
  {:usa "usa-wizard-masters-prod-1.fly.dev"
   :germany "eu-wizard-masters-prod-1.fly.dev"})

(defn ws-connect [{:keys [url on-open on-message on-close on-error]}]
  (println "Connecting WS...")
  (reset! socket (js/WebSocket. url))
  (j/assoc! @socket :binaryType "arraybuffer")
  (some->> on-open (j/call @socket :addEventListener "open"))
  (some->> on-message (j/call @socket :addEventListener "message"))
  (some->> on-close (j/call @socket :addEventListener "close"))
  (some->> on-error (j/call @socket :addEventListener "error")))

(defn dispatch-pro
  ([pro]
   (dispatch-pro pro nil))
  ([pro data]
   (when (and @open? @socket)
     (j/call @socket :send (msg/pack {:pro pro
                                      :data data})))))

(defmulti dispatch-pro-response ffirst)

(defn disconnect []
  (some-> @socket (j/call :close)))

(defn- add-cg-username [m]
  (let [cg-username (re/query :player/cg-username)]
    (cond-> m
      (not (str/blank? cg-username)) (assoc :cg-username (str/trim cg-username)))))

(let [added? (atom false)]
  (defn- add-requested-room-id []
    (when-not @added?
      (reset! added? true)
      (when-let [room-id (ads/get-room-id-from-url)]
        (re/insert {:player/requested-room-id room-id})))))

(def end-game)

(defn get-server-url []
  (servers :usa)
  #_(if (= :usa (re/query :server/selected))
    (servers :usa)
    (servers :germany)))

(defn- get-ws-url []
  (let [token (utils/get-item :auth-token)
        cg-user-token (re/query :player/cg-user-token)]
    (cond-> (if config/dev?
              "ws://localhost:3000/ws"
              (str "wss://" (get-server-url) ":443/ws"))
      (not (str/blank? token)) (str "?auth=" token)
      (not (str/blank? cg-user-token)) (str (if (not (str/blank? token))
                                              "&"
                                              "?") "cg_auth=" cg-user-token))))

(defn- get-api-url [url]
  (if config/dev?
    "http://localhost:3000/api"
    (or url (str "https://" (get-server-url) "/api"))))

(defn connect []
  (when-not (re/query :network/connecting?)
    (re/fire-rules {:network/connecting? true
                    :network/error nil})
    (ws-connect {:url (get-ws-url)
                 :on-message (fn [event]
                               (dispatch-pro-response (msg/unpack (j/get event :data))))
                 :on-open (fn []
                            (println "WS connection established.")
                            (reset! open? true))
                 :on-close (fn [e]
                             (println "WS connection CLOSED.")
                             (reset! open? false)
                             (reset! socket nil)
                             (re/fire-rules {:network/connected? false
                                             :network/connecting? false
                                             :player/room-id nil
                                             :player/requested-to-join? false
                                             :game/started? false
                                             :game/ended? false
                                             :game/end-time nil
                                             :game/win? nil
                                             :game/ack-game-queue-time nil})
                             (end-game))
                 :on-error (fn [err]
                             (println "WS ERROR occurred!")
                             (reset! open? false)
                             (re/fire-rules {:network/connected? false
                                             :network/error err
                                             :network/connecting? false})
                             (scene.utils/exit-pointer-lock))})))

(defn ajax-xhrio-handler
  "ajax-request only provides a single handler for success and errors"
  [on-success on-error xhrio [success? response]]
  ;; see http://docs.closure-library.googlecode.com/git/class_goog_net_XhrIo.html
  (if success?
    (on-success response)
    (let [details (merge
                    {:uri (j/call xhrio :getLastUri)
                     :last-method (j/get xhrio :lastMethod_)
                     :last-error (j/call xhrio :getLastError)
                     :last-error-code (j/call xhrio :getLastErrorCode)
                     :debug-message (-> xhrio (j/call :getLastErrorCode) (errors/getDebugMessage))}
                    response)]
      (on-error details))))

(defn request->xhrio-options
  [{:as request
    :keys [on-success on-error]}]
  (let [api (new goog.net.XhrIo)]
    (-> request
        (assoc
          :api api
          :handler (partial ajax-xhrio-handler
                            #(on-success %)
                            #(on-error %)
                            api))
        (dissoc :on-success :on-error :on-request))))

(defmulti dispatch-pro-sync-response (fn [x] (-> x :id keyword)))

(defn dispatch-pro-sync [pro data]
  (let [request {:uri (get-api-url (:url data))
                 :method :post
                 :params {:pro pro
                          :auth (utils/get-item :auth-token)
                          :data data}
                 :timeout (or (:timeout data) 30000)
                 :on-success (fn [data]
                               (dispatch-pro-sync-response data))
                 :on-error (fn [e]
                             (dispatch-pro-sync-response (assoc e :id pro :fail? true)))
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})}
        xhrio (-> request request->xhrio-options ajax/ajax-request)]
    xhrio))

(defn ping-servers []
  (dispatch-pro-sync :ping-server {:url (str "https://" (:usa servers) "/api")
                                   :timeout 3000
                                   :region :usa
                                   :timestamp (js/Date.now)})
  (dispatch-pro-sync :ping-server {:url (str "https://" (:germany servers) "/api")
                                   :timeout 3000
                                   :region :germany
                                   :timestamp (js/Date.now)}))

(defmethod dispatch-pro-sync-response :leaderboard [{:keys [result error fail?]}]
  (cond
    fail? (re/insert :shop/show-error-modal :fail)
    error (re/insert :shop/show-error-modal error)
    result (re/insert :leaderboard/data result)))

(defmethod dispatch-pro-sync-response :sign-up [{:keys [result error fail?]}]
  (disconnect)
  (re/insert :login/processing? false)
  (cond
    fail? (re/insert :login/show-error-modal :fail)
    error (re/insert :login/show-error-modal error)
    result (do
             (re/fire-rules :game/login? false)
             (utils/set-item :auth-token (:auth result))
             (re/insert :player/data result))))

(defmethod dispatch-pro-sync-response :log-in [{:keys [result error fail?]}]
  (disconnect)
  (re/insert :login/processing? false)
  (cond
    fail? (re/insert :login/show-error-modal :fail)
    error (re/insert :login/show-error-modal error)
    result (do
             (re/fire-rules :game/login? false)
             (utils/set-item :auth-token (:auth result))
             (re/insert :player/data result))))

(defmethod dispatch-pro-sync-response :create-room [{:keys [result fail? error]}]
  (re/insert :game/creating-room? false)
  (when-let [room-id (:room-id result)]
    (println "Created room id: " room-id)
    (disconnect)
    (js/setTimeout connect 250)
    (re/insert {:game/share-room-link-modal {:success? true}
                :game/create-room-panel? false
                :player/requested-room-id room-id}))
  (when (or fail? error)
    (re/insert {:game/share-room-link-modal {:success? false}
                :player/requested-room-id nil})))

(defmethod dispatch-pro-sync-response :check-room-available [{:keys [result fail? error]}]
  (re/insert :game/checking-room? false)
  (cond
    (not (:room-available? result))
    (re/insert {:player/requested-room-id nil
                :game/share-room-link-modal {:room-not-available? true}})

    (:room-full? result)
    (re/insert {:player/requested-room-id nil
                :game/share-room-link-modal {:room-full? true}})

    (or fail? error)
    (re/insert {:game/share-room-link-modal {:success? false}
                :player/requested-room-id nil})

    :else (do
            (re/insert {:player/requested-room-id (:room-id result)
                        :game/create-room-panel? false})
            (disconnect)
            (js/setTimeout connect 250))))

(defmethod dispatch-pro-sync-response :get-booster [{:keys [result fail? error]}]
  (when result
    (api.sound/play :sound/collectable-speed)
    (let [{:keys [server-time user-data]} result]
      (re/insert {:player/data user-data
                  :game/server-time server-time}))))

(defmethod dispatch-pro-sync-response :get-server-time [{:keys [result]}]
  (when result
    (re/insert :game/server-time result)))

(defmethod dispatch-pro-sync-response :ping-server [{:keys [result]}]
  (when result
    (let [timestamp (:timestamp result)
          region (keyword (:region result))
          rtt (double (/ (- (js/Date.now) timestamp) 2))
          latency-key (if (= region :usa) :server/usa :server/germany)]
      (re/insert latency-key rtt))))

(defn select-server []
  (let [usa (re/query :server/usa)
        de (re/query :server/germany)]
    (if (or (and usa de (< usa de))
            (and usa (nil? de)))
      (re/insert :server/selected :usa)
      (re/insert :server/selected :germany))))
