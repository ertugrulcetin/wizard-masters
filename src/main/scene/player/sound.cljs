(ns main.scene.player.sound
  (:require
    [main.api.sound :refer [reg-sound]]))

(reg-sound
  :sound/air-whoosh
  {:url "sound/air_whoosh.wav"
   :speed 1.2
   :preload? true
   :volume 0.5})

(reg-sound
  :sound/levitate
  {:url "sound/levitate_whoosh.mp3"
   :preload? true})

#_(reg-sound
    :sound/air-whoosh-spatial
    {:url "sound/air_whoosh.wav"
     :speed 1.2
     :volume 0.8
     :spatial? true
     :preload? true})

#_(reg-sound
    :sound/fire-whoosh
    {:speed 0.8
     :url "sound/fire_whoosh.mp3"
     :preload? true})

#_(reg-sound
    :sound/fire-impact
    {:url "sound/fire_impact.mp3"
     :spatial? true
     :preload? true})

#_(reg-sound
    :sound/death
    {:url "sound/splash.mp3"
     :spatial? true
     :preload? true})

(reg-sound
  :sound/fire-projectile-hit
  {:url "sound/fire_hit.mp3"
   :volume 1
   :spatial? true
   :preload? true})

#_(reg-sound
    :sound/male-roll
    {:url "sound/male_roll.mp3"
     :volume 0.4})

#_(reg-sound
    :sound/male-jump
    {:url "sound/male_roll.mp3"
     :volume 0.2
     :pitch 0.9})

#_(reg-sound
    :sound/step-1
    {:url "sound/ground_step_1.mp3"})

#_(reg-sound
    :sound/step-2
    {:url "sound/ground_step_2.mp3"})

(reg-sound
  :sound/step-3
  {:url "sound/ground_step_3.mp3"})

(reg-sound
  :sound/step-4
  {:url "sound/ground_step_4.mp3"})

(reg-sound
  :sound/dash-sound
  {:url "sound/dash.mp3"
   :volume 0.8})

(reg-sound
  :sound/landing
  {:url "sound/land2.mp3"})

(reg-sound
  :sound/nova-impact
  {:url "sound/nova_impact.mp3"
   :speed 1.5
   :spatial? true})

(reg-sound
  :sound/ice-tornado-wind
  {:url "sound/ice_tornado.mp3"
   :spatial? true})

(reg-sound
  :sound/ice-whoosh
  {:url "sound/ice_whoosh.mp3"
   :volume 0.5
   :spatial? true})

(reg-sound
  :sound/ice-hit
  {:url "sound/ice_hit.mp3"
   :spatial? true})

(reg-sound
  :sound/heartbeat
  {:url "sound/heartbeat.mp3"
   :loop? true})

(reg-sound
  :sound/victory
  {:url "sound/victory.mp3"})

(reg-sound
  :sound/defeat
  {:url "sound/defeat.mp3"})

(reg-sound
  :sound/bow-draw
  {:url "sound/bow_draw.mp3"
   :speed 1.5})

(reg-sound
  :sound/scream-1
  {:url "sound/scream_1.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-2
  {:url "sound/scream_2.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-3
  {:url "sound/scream_3.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-4
  {:url "sound/scream_4.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-5
  {:url "sound/scream_5.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-6
  {:url "sound/scream_6.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-7
  {:url "sound/scream_7.mp3"
   :spatial? true})

(reg-sound
  :sound/scream-8
  {:url "sound/scream_8.mp3"
   :spatial? true})

(reg-sound
  :sound/collectable-hp
  {:url "sound/collectable_hp_potion.mp3"})

#_(reg-sound
    :sound/collectable-mp
    {:url "sound/collectable_mp_potion.mp3"})

#_(reg-sound
    :sound/collectable-mp-air-regen
    {:url "sound/collectable_mp_air_regen.mp3"})

#_(reg-sound
    :sound/collectable-speed
    {:url "sound/collectable_run_fast.mp3"})

#_(reg-sound
    :sound/collectable-finish
    {:url "sound/collectable_finish.mp3"})

(reg-sound
  :sound/electric-light-staff
  {:url "sound/light-staff.mp3"
   :volume 0.2
   :spatial? true
   :preload? true})

(reg-sound
  :sound/electric-light-staff-player
  {:url "sound/light-staff.mp3"
   :volume 0.4
   :loop? true
   :preload? true})

(reg-sound
  :sound/electric-light-strike
  {:url "sound/light-strike.mp3"
   :volume 0.5
   :speed 1.5
   :spatial? true
   :preload? true})

(reg-sound
  :sound/electric-light-strike-thunder
  {:url "sound/light-strike-thunder.mp3"
   :volume 0.7
   :spatial? true
   :preload? true})

(reg-sound
  :sound/wind-slash
  {:url "sound/wind-slash.mp3"
   :volume 0.7
   :spatial? true
   :preload? true})

(reg-sound
  :sound/wind-hit
  {:url "sound/wind-hit.mp3"
   :spatial? true
   :preload? true})

(reg-sound
  :sound/wind-tornado-cast
  {:url "sound/wind-tornado-cast.mp3"
   :speed 1
   :spatial? true
   :preload? true})

(reg-sound
  :sound/wind-tornado-blow
  {:url "sound/wind-tornado.mp3"
   :speed 1
   :spatial? true
   :preload? true})

(reg-sound
  :sound/coin
  {:url "sound/coin.mp3"
   :volume 1
   :preload? true})

(reg-sound
  :sound/toxic-whoosh
  {:url "sound/toxic-whoosh.mp3"
   :volume 1
   :preload? true
   :spatial? true})

(reg-sound
  :sound/toxic-explode
  {:url "sound/toxic-spell-explode.mp3"
   :volume 0.6
   :pitch 1.5
   :preload? true
   :spatial? true})

(reg-sound
  :sound/toxic-cloud-explode
  {:url "sound/toxic-cloud.mp3"
   :speed 0.8
   :pitch 0.8
   :volume 1
   :preload? true
   :spatial? true})

(reg-sound
  :sound/rock-hit
  {:url "sound/rock.mp3"
   :volume 0.4
   :preload? true
   :spatial? true})

(reg-sound
  :sound/rock-wall
  {:url "sound/rock_wall.mp3"
   :volume 1
   :preload? true
   :spatial? true})

(reg-sound
  :sound/map-change-countdown
  {:url "sound/countdown.mp3"
   :volume 1
   :preload? true})
