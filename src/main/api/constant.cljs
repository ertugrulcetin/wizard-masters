(ns main.api.constant
  (:require
    ["@babylonjs/core/Debug/skeletonViewer" :refer [SkeletonViewer]]
    ["@babylonjs/core/Events/keyboardEvents" :refer [KeyboardEventTypes]]
    ["@babylonjs/core/Events/pointerEvents" :refer [PointerEventTypes]]
    ["@babylonjs/core/Materials/GreasedLine/greasedLineMaterialInterfaces" :refer [GreasedLineMeshColorMode
                                                                                   GreasedLineMeshMaterialType]]
    ["@babylonjs/core/Materials/material" :refer [Material]]
    ["@babylonjs/core/Maths/math" :refer [Vector2 Vector3 Vector4]]
    ["@babylonjs/core/Maths/math.color" :refer [Color3]]
    ["@babylonjs/core/Meshes/abstractMesh" :refer [AbstractMesh]]
    ["@babylonjs/core/Meshes/mesh" :refer [Mesh]]
    ["@babylonjs/core/Particles/particleSystem" :refer [ParticleSystem]]
    ["@babylonjs/core/Physics/v2/IPhysicsEnginePlugin" :refer [PhysicsMotionType]]
    ["@babylonjs/gui/2D" :refer [Control]]
    ["@babylonjs/gui/2D/controls" :refer [Image]]
    [applied-science.js-interop :as j]))

(def v3-up (j/call Vector3 :Up))
(def v3-forward (j/call Vector3 :Forward))
(def v3-backward (j/call Vector3 :Backward))
(def v3-left (j/call Vector3 :Left))
(def v3-right (j/call Vector3 :Right))

(def animation-type-v3 :ANIMATIONTYPE_VECTOR3)
(def animation-type-float :ANIMATIONTYPE_FLOAT)
(def animation-loop-cons :ANIMATIONLOOPMODE_CONSTANT)
(def animation-loop-cycle :ANIMATIONLOOPMODE_CYCLE)

(def easing-ease-in :EASINGMODE_EASEIN)
(def easing-ease-out :EASINGMODE_EASEOUT)
(def easing-ease-in-out :EASINGMODE_EASEINOUT)

(def gui-horizontal-align-left (j/get Control :HORIZONTAL_ALIGNMENT_LEFT))
(def gui-horizontal-align-right (j/get Control :HORIZONTAL_ALIGNMENT_RIGHT))
(def gui-horizontal-align-center (j/get Control :HORIZONTAL_ALIGNMENT_CENTER))
(def gui-vertical-align-center (j/get Control :VERTICAL_ALIGNMENT_CENTER))
(def gui-vertical-align-top (j/get Control :VERTICAL_ALIGNMENT_TOP))
(def gui-vertical-align-bottom (j/get Control :VERTICAL_ALIGNMENT_BOTTOM))

(def gui-text-wrapping-word-wrap :WordWrap)

(def mesh-billboard-mode-none (j/get Mesh :BILLBOARDMODE_NONE))
(def mesh-billboard-mode-all (j/get Mesh :BILLBOARDMODE_ALL))
(def mesh-billboard-mode-y (j/get Mesh :BILLBOARDMODE_Y))

(defn color-white [] (j/call Color3 :White))
(defn color-black [] (j/call Color3 :Black))
(defn color-yellow [] (j/call Color3 :Yellow))
(defn color-red [] (j/call Color3 :Red))
(defn color-teal [] (j/call Color3 :Teal))
(defn color-gray [] (j/call Color3 :Gray))

(def coordinates-mode-skybox :SKYBOX_MODE)

(def mesh-default-side (j/get Mesh :DEFAULTSIDE))
(def mesh-double-side (j/get Mesh :DOUBLESIDE))

(def mat-alpha-blend (j/get Material :MATERIAL_ALPHABLEND))
(def mat-alpha-test-and-blend (j/get Material :ATERIAL_ALPHATESTANDBLEND))

(def pointer-type-down (j/get PointerEventTypes :POINTERDOWN))
(def pointer-type-up (j/get PointerEventTypes :POINTERUP))
(def pointer-type-wheel (j/get PointerEventTypes :POINTERWHEEL))
(def pointer-type-double-tap (j/get PointerEventTypes :POINTERDOUBLETAP))
(def pointer-type-move (j/get PointerEventTypes :POINTERMOVE))
(def pointer-type-tap (j/get PointerEventTypes :POINTERTAP))
(def pointer-type-pick (j/get PointerEventTypes :POINTERPICK))

(def keyboard-type-key-down (j/get KeyboardEventTypes :KEYDOWN))
(def keyboard-type-key-up (j/get KeyboardEventTypes :KEYUP))

(def particle-blend-mode-standard (j/get ParticleSystem :BLENDMODE_STANDARD))
(def particle-blend-mode-add (j/get ParticleSystem :BLENDMODE_ADD))
(def particle-blend-mode-multiply (j/get ParticleSystem :BLENDMODE_MULTIPLY))
(def particle-blend-mode-multiply-add (j/get ParticleSystem :BLENDMODE_MULTIPLYADD))
(def particle-blend-mode-one-one (j/get ParticleSystem :BLENDMODE_ONEONE))

(def particle-billboard-mode-stretched (j/get ParticleSystem :BILLBOARDMODE_STRETCHED))
(def particle-billboard-mode-y (j/get ParticleSystem :BILLBOARDMODE_Y))
(def particle-billboard-mode-all (j/get ParticleSystem :BILLBOARDMODE_ALL))

(def greased-line-material-pbr (j/get GreasedLineMeshMaterialType :MATERIAL_TYPE_PBR))
(def greased-line-color-mode-multi (j/get GreasedLineMeshColorMode :COLOR_MODE_MULTIPLY))

(def motion-type-static (j/get PhysicsMotionType :STATIC))
(def motion-type-dynamic (j/get PhysicsMotionType :DYNAMIC))

;; STRETCH_UNIFORM
(def stretch-fill (j/get Image :STRETCH_FILL))
(def stretch-uniform (j/get Image :STRETCH_UNIFORM))
(def stretch-extend (j/get Image :STRETCH_EXTEND))

(def occlusion-query-algorithm-type-conservative (j/get AbstractMesh :OCCLUSION_ALGORITHM_TYPE_CONSERVATIVE))
(def occlusion-type-optimistic (j/get AbstractMesh :OCCLUSION_TYPE_OPTIMISTIC))

(def skeleton-viewer-display-lines (j/get SkeletonViewer :DISPLAY_LINES))
(def skeleton-viewer-display-spheres (j/get SkeletonViewer :DISPLAY_SPHERES))
(def skeleton-viewer-display-sphere-and-spurs (j/get SkeletonViewer :DISPLAY_SPHERE_AND_SPURS))

(def collision-group-environment 0x0001)
(def collision-group-player 0x0002)
(def collision-group-kill-splash 0x0003)
(def collision-group-kill-splash-surface 0x0004)
(def collision-group-other-players 0x0005)
(def collision-group-collectables 0x0006)
(def collision-group-world-trigger-box 0x0007)
