(ns main.ui.subs
  (:require
    [main.common :as common]
    [main.utils :as utils]
    [re-frame.core :refer [reg-sub]]))

(reg-sub
  ::name
  (fn [db]
    (:name db)))

(reg-sub
  ::game-loading?
  (fn [db]
    (:game/loading? db)))

(reg-sub
  ::fps
  (fn [db]
    (:fps db)))

(reg-sub
  ::current-time
  (fn [db]
    (:current-time db)))

(reg-sub
  ::pointer-locked?
  (fn [db]
    (:pointer-locked? db)))

(reg-sub
  ::cooldown-roll
  (fn [db]
    (:cooldown/roll db)))

(reg-sub
  ::cooldown-spell
  (fn [db]
    (:cooldown/spell db)))

(reg-sub
  ::cooldown-spell-super-nova
  (fn [db]
    (:cooldown/spell-super-nova db)))

(reg-sub
  ::cooldown-spell-toxic-cloud
  (fn [db]
    (:cooldown/spell-toxic-cloud db)))

(reg-sub
  ::cooldown-spell-rock-wall
  (fn [db]
    (:cooldown/spell-rock-wall db)))

(reg-sub
  ::cooldown-spell-ice-tornado
  (fn [db]
    (:cooldown/spell-ice-tornado db)))

(reg-sub
  ::cooldown-spell-ice-arrow
  (fn [db]
    (:cooldown/spell-ice-arrow db)))

(reg-sub
  ::cooldown-spell-wind-slash
  (fn [db]
    (:cooldown/spell-wind-slash db)))

(reg-sub
  ::cooldown-spell-wind-tornado
  (fn [db]
    (:cooldown/spell-wind-tornado db)))

(reg-sub
  ::cooldown-spell-light-staff
  (fn [db]
    (:cooldown/spell-light-staff db)))

(reg-sub
  ::cooldown-spell-light-strike
  (fn [db]
    (:cooldown/spell-light-strike db)))

(reg-sub
  ::player-total-health
  (fn [db]
    (:player/total-health db)))

(reg-sub
  ::player-current-health
  (fn [db]
    (:player/current-health db)))

(reg-sub
  ::players-stats
  (fn [db]
    (:stats (:players/stats db))))

(reg-sub
  ::team-kills
  (fn [db]
    (dissoc (:players/stats db) :stats)))

(reg-sub
  ::network-ping
  (fn [db]
    (:network/ping db)))

(reg-sub
  ::player-room-id
  (fn [db]
    (:player/room-id db)))

(reg-sub
  ::player-kill-info
  (fn [db]
    (:player/kill-info db)))

(reg-sub
  ::network-connected?
  (fn [db]
    (:network/connected? db)))

(reg-sub
  ::network-connecting?
  (fn [db]
    (:network/connecting? db)))

(reg-sub
  ::network-error?
  (fn [db]
    (boolean (:network/error db))))

(reg-sub
  ::who-killed-who
  (fn [db]
    (let [kills (:players/who-killed-who db)
          current-time (:current-time db)]
      (->> kills
           (filter (fn [[time]]
                     (<= (- current-time time) 4000)))
           (map (fn [[_ player1 player2]]
                  [player1 player2]))))))

(reg-sub
  ::chat-messages
  (fn [db]
    (let [messages (:chat/messages db)
          chat-focus? (:player/chat-focus? db)]
      (if chat-focus?
        messages
        (filterv
          (fn [{:keys [time]}]
            (<= (- (:current-time db) time) 10000))
          messages)))))

(reg-sub
  ::chat-focus?
  (fn [db]
    (:player/chat-focus? db)))

(reg-sub
  ::roll-enabled?
  (fn [db]
    (or (:player/forward? db)
        (:player/backward? db)
        (:player/left? db)
        (:player/right? db)
        (:player/jumping? db))))

(reg-sub
  ::player-mana
  (fn [db]
    (:player/mana db)))

(reg-sub
  ::player-team
  (fn [db]
    (:player/team db)))

(reg-sub
  ::player-ground?
  (fn [db]
    (:player/ground? db)))

(reg-sub
  ::player-levitate?
  (fn [db]
    (:player/levitate? db)))

(reg-sub
  ::player-spelling?
  (fn [db]
    (or (:player/levitate? db)
        (:player/spell? db)
        (:player/spell-super-nova? db)
        (:player/spell-ice-arrow? db)
        (:player/spell-ice-tornado? db)
        (:player/dash? db))))

(reg-sub
  ::game-started?
  (fn [db]
    (:game/started? db)))

(reg-sub
  ::game-ended?
  (fn [db]
    (:game/ended? db)))

(reg-sub
  ::win?
  (fn [db]
    (:game/win? db)))

(reg-sub
  ::click-to-play?
  (fn [db]
    (and (:network/connected? db)
         (> (:player/current-health db) 0)
         (or (and (not (:game/mobile? db))
                  (not (:pointer-locked? db)))
             (:player/paused? db))
         (not (:game/ended? db)))))

(reg-sub
  ::click-to-join?
  (fn [db]
    (and (not (:player/room-id db))
         (not (:player/requested-to-join? db))
         (not (:game/started? db))
         (or (nil? (:game/end-time db))
             (>= (- (js/Date.now) (:game/end-time db)) common/new-game-after-milli-secs)))))

(reg-sub
  ::requested-to-join?
  (fn [db]
    (:player/requested-to-join? db)))

(reg-sub
  ::requested-room-id
  (fn [db]
    (:player/requested-room-id db)))

(reg-sub
  ::next-game-ready-in-secs
  (fn [db]
    (when (and (:game/end-time db)
               (not (:player/requested-to-join? db)))
      (let [time-left (- (+ common/new-game-after-milli-secs (:game/end-time db)) (:current-time db))]
        (when (>= time-left 0)
          (utils/millis->secs time-left))))))

(reg-sub
  ::player-died?
  (fn [db]
    (:player/died? db)))

(reg-sub
  ::respawn-left-in-secs
  (fn [db]
    (cond
      (:player/died? db)
      (let [time-left (- (+ (:player/respawn-duration db) (:player/died-time db)) (:current-time db))]
        (when (>= time-left 0)
          (utils/millis->secs time-left)))

      (:player/paused? db)
      (let [time-left (- (+ (:player/respawn-duration db) (:player/paused-time db)) (:current-time db))]
        (when (>= time-left 0)
          (utils/millis->secs time-left))))))

(reg-sub
  ::player-last-time-mp-potion-collected
  (fn [db]
    (:player/last-time-mp-potion-collected db)))

(reg-sub
  ::player-speed-booster-until
  (fn [db]
    (let [remained (- (:player/speed-booster-until db) (:current-time db))]
      (when (> remained 0)
        (int (/ remained 1000))))))

(reg-sub
  ::player-focus?
  (fn [db [_ player-id]]
    (let [current-player? (= player-id (:player/id db))]
      (if current-player?
        (and (> (:player/current-health db) 0) (:player/focus? db))
        (let [player (get (:players db) player-id)]
          (and (> (:health player) 0) (:focus? player)))))))

(reg-sub
  ::current-player-focus?
  (fn [db]
    (and (:pointer-locked? db) (:player/focus? db))))

(reg-sub
  ::show-loading-progress?
  (fn [db]
    (:scene/show-loading-progress? db)))

(reg-sub
  ::loading-progress
  (fn [db]
    (:scene/loading-progress db)))

(reg-sub
  ::played-before?
  (fn [db]
    (-> db :player/data-store :number-of-plays (> 0))))

(reg-sub
  ::show-dc-boost?
  (fn [db]
    (-> db :player/data-store :number-of-dc-clicks (< 2))))

(reg-sub
  ::game-join-failed
  (fn [db]
    (:game/join-failed db)))

(reg-sub
  ::player-primary-element
  (fn [db]
    (:player/primary-element db)))

(reg-sub
  ::player-secondary-element
  (fn [db]
    (:player/secondary-element db)))

(reg-sub
  ::ready-to-play?
  (fn [db]
    (:player/game-started? db)))

(reg-sub
  ::settings-music-volume
  (fn [db]
    (-> db :settings :music-volume)))

(reg-sub
  ::settings-sfx-distance-cutoff
  (fn [db]
    (-> db :settings :sfx-distance-cutoff)))

(reg-sub
  ::settings-quality
  (fn [db]
    (-> db :settings :quality)))

(reg-sub
  ::settings-anti-alias?
  (fn [db]
    (-> db :settings :anti-alias?)))

(reg-sub
  ::settings-mouse-invert?
  (fn [db]
    (-> db :settings :invert-mouse?)))

(reg-sub
  ::settings-mouse-sensitivity
  (fn [db]
    (-> db :settings :mouse-sensitivity)))

(reg-sub
  ::settings-mouse-zoom-sensitivity
  (fn [db]
    (-> db :settings :mouse-zoom-sensitivity)))

(reg-sub
  ::unlocked-wind-element?
  (fn [db]
    (:player/unlocked-wind-element? db)))

(reg-sub
  ::unlocked-light-element?
  (fn [db]
    (:player/unlocked-light-element? db)))

(reg-sub
  ::unlocked-toxic-element?
  (fn [db]
    (:player/unlocked-toxic-element? db)))

(reg-sub
  ::unlocked-earth-element?
  (fn [db]
    (:player/unlocked-earth-element? db)))

(reg-sub
  ::wind-tornado-stunned-time-left
  (fn [db]
    (- (:player/wind-tornado-stunned-end-time db) (:current-time db))))

(reg-sub
  ::stun-time-left
  (fn [db]
    (- (:player/freeze-end-time db) (:current-time db))))

(reg-sub
  ::puddle-time-left
  (fn [db]
    (- (:player/puddle-end-time db) (:current-time db))))

(reg-sub
  ::adblock-modal-open?
  (fn [db]
    (:ad/adblock-modal-open? db)))

(reg-sub
  ::shop-opened?
  (fn [db]
    (:game/shop? db)))

(reg-sub
  ::settings-game-mode
  (fn [db]
    (-> db :settings :game-mode (or :solo-death-match))))

(reg-sub
  ::shop-hovered-item
  (fn [db]
    (:id (:shop/equip db))))

(reg-sub
  ::shop-selected-item
  (fn [db]
    (:shop/selected-item db)))

(reg-sub
  ::shop-purchasing?
  (fn [db]
    (:shop/purchasing? db)))

(reg-sub
  ::shop-purchased-items
  (fn [db]
    (->> db :player/data :purchases (map keyword) set)))

(reg-sub
  ::player-coins
  (fn [db]
    (or (->> db :player/data :coins) 0)))

(reg-sub
  ::shop-error-modal
  (fn [db]
    (:shop/show-error-modal db)))

(reg-sub
  ::login-error-modal
  (fn [db]
    (:login/show-error-modal db)))

(reg-sub
  ::player-equipped
  (fn [db]
    (->> db :player/data :equipped vals (map keyword) set)))

(reg-sub
  ::player-cg-user-id?
  (fn [db]
    (->> db :player/data :cg_user_id some?)))

(reg-sub
  ::player-username
  (fn [db]
    (->> db :player/data :username)))

(reg-sub
  ::player-auth-completed?
  (fn [db]
    (:player/auth-completed? db)))

(reg-sub
  ::leaderboard?
  (fn [db]
    (:game/leaderboard? db)))

(reg-sub
  ::leaderboard
  (fn [db]
    (:leaderboard/data db)))

(reg-sub
  ::login-processing?
  (fn [db]
    (:login/processing? db)))

(reg-sub
  ::login-panel-open?
  (fn [db]
    (:game/login? db)))

(reg-sub
  ::create-room-panel-open?
  (fn [db]
    (:game/create-room-panel? db)))

(reg-sub
  ::creating-room?
  (fn [db]
    (:game/creating-room? db)))

(reg-sub
  ::checking-room?
  (fn [db]
    (:game/checking-room? db)))

(reg-sub
  ::signed-up?
  (fn [db]
    (-> db :player/data :account)))

(reg-sub
  ::sdk-cg?
  (fn [db]
    (:sdk/cg? db)))

(reg-sub
  ::game-mode
  (fn [db]
    (:game/mode db)))

(reg-sub
  ::share-room-link-modal
  (fn [db]
    (:game/share-room-link-modal db)))

(reg-sub
  ::player-incoming-enemy-spells
  (fn [db]
    (let [current-time (:current-time db)
          spells (:player/incoming-enemy-spells db)]
      (->> spells
           (filter (fn [{:keys [time]}]
                     (> (+ time 1500) current-time)))
           (map :spell)))))

(reg-sub
  ::map-change-remained
  (fn [db]
    (and (:game/server-time db)
         (:game/map-change-time db)
         (- (:game/map-change-time db) (:game/server-time db)))))

(reg-sub
  ::booster-active?
  (fn [db [_ booster]]
    (and (-> db :player/data booster)
         (< (:game/server-time db) (-> db :player/data booster)))))

(reg-sub
  ::all-boosters-active?
  (fn [db]
    (every?
      (fn [booster]
        (and (-> db :player/data booster)
             (< (:game/server-time db) (-> db :player/data booster))))
      [:booster_regen_mana
       :booster_defense
       :booster_damage
       :booster_regen_hp
       :booster_coin
       :booster_discord
       :booster_cooldown
       :booster_stun
       :booster_root])))

(reg-sub
  ::non-active-boosters-count
  (fn [db]
    (count
      (remove
        (fn [booster]
          (and (-> db :player/data booster)
               (< (:game/server-time db) (-> db :player/data booster))))
        [:booster_regen_mana
         :booster_defense
         :booster_damage
         :booster_regen_hp
         :booster_coin
         :booster_discord
         :booster_cooldown
         :booster_stun
         :booster_root]))))

(reg-sub
  ::booster-panel-open?
  (fn [db]
    (:game/booster-panel? db)))

(reg-sub
  ::player-boosts?
  (fn [db [_ player-id]]
    (get-in db [:players player-id :boost?])))

(reg-sub
  ::mobile?
  (fn [db]
    (:game/mobile? db)))

(reg-sub
  ::orientation-portrait?
  (fn [db]
    (= :portrait (:screen/orientation db))))
