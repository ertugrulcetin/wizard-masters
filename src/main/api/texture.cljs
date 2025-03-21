(ns main.api.texture
  (:require
    ["@babylonjs/core/Materials/Textures/texture" :refer [Texture]]
    [applied-science.js-interop :as j]
    [main.api.asset :as api.asset]
    [main.api.core :as api.core]))

(defn reg-texture [id opts]
  (let [texture-name (name id)
        opts (assoc opts :id id
                    :name texture-name
                    :type :texture)]
    (when (api.core/get-scene)
      (some-> (api.asset/get-asset id) (j/call :updateURL (:url opts))))
    (j/assoc-in! api.core/db [:assets-regs id] opts)
    opts))

#_(reg-texture
  :texture/checkerboard
  {:url "img/texture/checkerboard.png"
   :preload? true
   :on-success (fn [tex]
                 (j/assoc! tex :uScale 15 :vScale 15))})

(reg-texture
  :texture/smoke-4
  {:url "img/texture/smoke_04.png"})

(reg-texture
  :texture/explode
  {:url "img/texture/square.png"})

;; basak <3 ertus <3 tifi <3 hera
(reg-texture
  :texture/fantasy-hero-blue
  {:url "img/texture/PolygonFantasyHero_Texture_01_A.png"
   :preload? true})

(reg-texture
  :texture/fantasy-hero-red
  {:url "img/texture/PolygonFantasyHero_Texture_02_A.png"
   :preload? true})

(reg-texture
  :texture/fantasy-hero-no-team
  {:url "img/texture/PolygonFantasyHero_Texture_03_A.png"
   :preload? true})

#_(reg-texture
  :texture/fantasy-rivals
  {:url "img/texture/FantasyRivals_Texture_01_A.png"
   :preload? true})

(reg-texture
  :texture/kingdom-1-a
  {:url "img/texture/PolygonFantasyKingdom_Texture_01_A.png"
   :preload? true})

(reg-texture
  :texture/kingdom-1-a-normal
  {:url "img/texture/PolygonFantasyKingdom_Texture_Normal_01.png"
   :preload? true})

(reg-texture
  :texture/fire-ball-sphere
  {:url "img/texture/fire.jpg"
   :preload? true})

(reg-texture
  :texture/star
  {:url "img/texture/star.png"})

(reg-texture
  :texture/dash
  {:url "img/texture/dash.png"})

(reg-texture
  :texture/smoke-sprite
  {:url "img/texture/smoke_sprite.png"
   :preload? true})

(reg-texture
  :texture/flare
  {:url "img/texture/flare.png"})

(reg-texture
  :texture/smoke-6
  {:url "img/texture/smoke_6.png"})

(reg-texture
  :texture/ay
  {:url "img/texture/ay.png"})

(reg-texture
  :texture/circle
  {:url "img/texture/circle.png"
   :preload? true})

(reg-texture
  :texture/ice-wind
  {:url "img/texture/ice_vfx.png"
   :preload? true})

(reg-texture
  :texture/ice-particle
  {:url "img/texture/ice_particle.png"})

(reg-texture
  :texture/ice-arrow
  {:url "img/texture/ice_arrow.png"})

(reg-texture
  :texture/kill-splash
  {:url "img/texture/kill-splash.png"})

(reg-texture
  :texture/kill-surface
  {:url "img/texture/kill-surface.png"})

(reg-texture
  :texture/light-staff
  {:url "img/texture/light-staff.png"
   ;; :preload? true
   })

(reg-texture
  :texture/light-burst
  {:url "img/texture/light-burst.png"
   :preload? true})

(reg-texture
  :texture/cloud
  {:url "img/texture/cloud.png"
   :preload? true})

(reg-texture
  :texture/wind-sprites
  {:url "img/texture/wind-sprites.png"
   :preload? true})

(reg-texture
  :texture/wind-hit-sprites
  {:url "img/texture/wind-hit-sprites.png"
   :preload? true})

(reg-texture
  :texture/shop-bg
  {:url "img/texture/bgShop.png"
   :preload? true})
