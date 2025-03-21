(ns main.ads
  (:require
    [applied-science.js-interop :as j]
    [cljs.core.async :as a]
    [clojure.string :as str]
    [main.api.sound :as api.sound]
    [main.rule-engine :as re]
    [main.scene.settings :as settings]
    [main.utils :as utils]))

(defn init []
  (let [p (a/promise-chan)]
    (cond
      (j/get js/window :PokiSDK)
      (when-let [poki (j/get js/window :PokiSDK)]
        (-> (j/call poki :init)
            (j/call :then (fn []
                            (a/put! p true)
                            (js/console.log "Poki SDK successfully initialized")))
            (j/call :catch
                    (fn []
                      (js/console.log "Poki SDK init failed.")))))

      (j/get-in js/window [:CrazyGames :SDK])
      (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
        (-> (j/call cg :init)
            (j/call :then (fn []
                            (a/put! p true)
                            (js/console.log "CrazyGames SDK successfully initialized")
                            (re/fire-rules {:ad/init? true
                                            :sdk/cg? true})))
            (j/call :catch
                    (fn []
                      (js/console.log "CrazyGames SDK init failed.")))))

      :else (a/put! p true))
    p))

(defn game-loading-start []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:game :loadingStart])))

(defn game-loading-finished []
  (when-let [poki (j/get js/window :PokiSDK)]
    (j/call poki :gameLoadingFinished))
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:game :loadingStop])))

(defn gameplay-start []
  (when-let [poki (j/get js/window :PokiSDK)]
    (j/call poki :gameplayStart))
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:game :gameplayStart])))

(defn gameplay-stop []
  (when-let [poki (j/get js/window :PokiSDK)]
    (j/call poki :gameplayStop))
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:game :gameplayStop])))

(defn set-username [f]
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (-> (j/call-in cg [:user :getUser])
        (j/call :then (fn [user]
                        (f (j/get user :username)))))))

(defn user-available? []
  (j/get-in js/window [:CrazyGames :SDK :user :isUserAccountAvailable]))

(defn get-user-token []
  (let [p (a/promise-chan)]
    (if (user-available?)
      (try
        (if-let [cg (j/get-in js/window [:CrazyGames :SDK])]
          (-> (j/call-in cg [:user :getUserToken])
              (j/call :then (fn [token]
                              (println "User token present")
                              (when-not (str/blank? token)
                                (re/insert :player/cg-user-token token)
                                (utils/set-item :cg-logged-in? true))
                              (a/put! p true)))
              (j/call :catch (fn []
                               (println "No user token")
                               (when (utils/get-item :cg-logged-in?)
                                 (utils/set-item :cg-logged-in? false))
                               (a/put! p true))))
          (a/put! p true))
        (catch js/Error _
          (a/put! p true)))
      (a/put! p true))
    p))

(defn add-auth-listener []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (-> (j/call-in cg [:user :addAuthListener]
                   (fn []
                     (j/call-in js/window [:location :reload]))))))

(defn show-auth-prompt [on-success]
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (-> (j/call-in cg [:user :showAuthPrompt])
        (j/call :then on-success))))

(defn get-invite-link []
  (if-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (when-let [room-id (re/query :player/room-id)]
      (-> (j/call-in cg [:game :inviteLink] #js {:roomId room-id})))
    (str "https://wizardmasters.io?roomId=" (re/query :player/room-id))))

(defn get-created-room-link []
  (if-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (when-let [room-id (re/query :player/requested-room-id)]
      (-> (j/call-in cg [:game :inviteLink] #js {:roomId room-id})))
    (str "https://wizardmasters.io?roomId=" (re/query :player/requested-room-id))))

(defn get-room-id-from-url []
  (if-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (-> (j/call-in cg [:game :getInviteParam] "roomId"))
    (let [url (j/get-in js/window [:location :href])
          obj (js/URL. url)]
      (j/call-in obj [:searchParams :get] "roomId"))))

(defn show-invite-button []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (when-let [room-id (re/query :player/room-id)]
      (j/call-in cg [:game :showInviteButton] #js {:roomId room-id}))))

(defn hide-invite-button []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:game :hideInviteButton])))

(defn instant-join? []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/get-in cg [:game :isInstantMultiplayer])))

(defn- ad-started []
  (api.sound/stop-audio-element "background-music")
  (js/console.log "Start ad")
  (re/fire-rules :ad/video-running? true)
  (api.sound/set-global-volume 0))

(defn- ad-error [e]
  (re/fire-rules :ad/video-running? false)
  (js/console.log "Error ad" e)
  (api.sound/set-global-volume (settings/get-setting :music-volume)))

(defn- ad-finished []
  (js/console.log "End ad")
  (re/fire-rules :ad/video-running? false)
  (api.sound/set-global-volume (settings/get-setting :music-volume)))

(defn request-midgame []
  (when-let [poki (j/get js/window :PokiSDK)]
    (re/fire-rules :ad/video-running? true)
    (-> poki
        (j/call :commercialBreak ad-started)
        (j/call :then ad-finished)
        (j/call :catch ad-error)))

  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (re/fire-rules :ad/video-running? true)
    (j/call-in cg [:ad :requestAd]
               "midgame"
               #js {:adFinished ad-finished
                    :adError ad-error
                    :adStarted ad-started})))

(defn request-rewarded [on-end]
  (if (re/query :ad/adblocker?)
    (re/insert :ad/adblock-modal-open? true)
    (do
      (when-let [poki (j/get js/window :PokiSDK)]
        (re/fire-rules :ad/video-running? true)
        (let [error? (atom false)]
          (-> poki
              (j/call :rewardedBreak ad-started)
              (j/call :then (fn [success]
                              (if success
                                (do
                                  (ad-finished)
                                  (when-not @error?
                                    (when on-end (on-end))))
                                (ad-finished))))
              (j/call :catch (fn [e]
                               (reset! error? true)
                               (ad-error e))))))
      (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
        (re/fire-rules :ad/video-running? true)
        (let [error? (atom false)]
          (j/call-in cg [:ad :requestAd]
                     "rewarded"
                     #js {:adFinished (fn []
                                        (ad-finished)
                                        (when-not @error?
                                          (when on-end (on-end))))
                          :adError (fn [e]
                                     (reset! error? true)
                                     (ad-error e))
                          :adStarted ad-started}))))))

(defn request-banner [{:keys [id width height]}]
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:banner :requestBanner] #js {:id id
                                                :width 728
                                                :height 90})))

(defn request-banners []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (re/insert :ad/last-time-banner-requested (js/Date.now))
    #_(j/call-in cg [:banner :requestBanner] #js {:id "banner-container-320-100"
                                                  :width 320
                                                  :height 100})
    (j/call-in cg [:banner :requestBanner] #js {:id "banner-container-300-250"
                                                :width 300
                                                :height 250})))

(defn clear-all-banners []
  (when-let [cg (j/get-in js/window [:CrazyGames :SDK])]
    (j/call-in cg [:banner :clearAllBanners])))
