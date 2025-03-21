(ns main.api.gui
  (:require
    ["@babylonjs/gui/2D" :refer [AdvancedDynamicTexture Control TextWrapping]]
    ["@babylonjs/gui/2D/controls" :refer [Button Image Rectangle TextBlock]]
    [applied-science.js-interop :as j]
    [main.api.core :as api.core])
  (:require-macros
    [main.macros :as m]))

(defn advanced-dynamic-texture []
  (let [advanced-texture (j/call-in AdvancedDynamicTexture [:CreateFullscreenUI] "UI")]
    (j/assoc! advanced-texture
              :idealWidth 3024
              :idealHeight 1964)
    (j/assoc! api.core/db :gui-advanced-texture advanced-texture)
    advanced-texture))

(defn get-control-by-name [adt name]
  (j/call adt :getControlByName name))

(defn image [name url]
  (Image. name url))

(defn button [name text]
  (j/call Button :CreateSimpleButton name text))

(defn img-text-button [{:keys [name text src]}]
  (j/call Button :CreateImageWithCenterTextButton name text src))

(defn add-control [container control]
  (j/call container :addControl control))

(defn rectangle [name & {:keys [corner-radius
                                background
                                width
                                height
                                thickness]
                         :as opts}]
  (let [rect (Rectangle. name)]
    (m/cond-doto rect
      thickness (j/assoc! :thickness thickness)
      width (j/assoc! :width width)
      height (j/assoc! :height height)
      corner-radius (j/assoc! :cornerRadius corner-radius)
      background (j/assoc! :background background))))

(defn create-for-mesh [mesh & {:keys [width height]}]
  (j/call AdvancedDynamicTexture :CreateForMesh mesh width height))

(defn text-block [{:keys [name
                          text
                          alpha
                          font-family
                          font-size-in-pixels
                          outline-width
                          outline-color
                          text-wrapping
                          text-horizontal-alignment
                          text-vertical-alignment
                          padding-top
                          padding-bottom
                          padding-right
                          padding-left
                          font-size
                          line-spacing
                          visible?
                          color
                          font-weight]}]
  (let [text-block (TextBlock. name text)]
    (m/cond-doto text-block
      font-size-in-pixels (j/assoc! :fontSizeInPixels font-size-in-pixels)
      text-wrapping (j/assoc! :textWrapping (j/get TextWrapping text-wrapping))
      text-horizontal-alignment (j/assoc! :textHorizontalAlignment text-horizontal-alignment)
      text-vertical-alignment (j/assoc! :textVerticalAlignment text-vertical-alignment)
      alpha (j/assoc! :alpha alpha)
      outline-width (j/assoc! :outlineWidth outline-width)
      outline-color (j/assoc! :outlineColor outline-color)
      font-family (j/assoc! :fontFamily font-family)
      line-spacing (j/assoc! :lineSpacing line-spacing)
      padding-top (j/assoc! :paddingTop padding-top)
      padding-bottom (j/assoc! :paddingBottom padding-bottom)
      padding-right (j/assoc! :paddingRight padding-right)
      padding-left (j/assoc! :paddingLeft padding-left)
      (some? visible?) (j/assoc! :isVisible visible?)
      font-size (j/assoc! :fontSize font-size)
      color (j/assoc! :color color)
      font-weight (j/assoc! :fontWeight font-weight))))

(defn link-with-mesh [e mesh]
  (j/call e :linkWithMesh mesh))
