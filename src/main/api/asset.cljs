(ns main.api.asset
  (:require
    ["@babylonjs/core/Misc/assetsManager" :refer [AssetsManager]]
    [applied-science.js-interop :as j]
    [cljs.core.async :as a]
    [main.api.core :as api.core]))

(defn assets-manager [& {:keys [on-progress
                                on-finish]}]
  (let [am (AssetsManager. (api.core/get-scene))]
    (j/assoc! api.core/db :assets-manager am)
    (j/assoc! am
              :onProgress on-progress
              :onFinish on-finish)))

(defn add-texture-task [id url on-success]
  (let [no-mipmap-or-options false
        invert-y false]
    (-> (j/call-in api.core/db [:assets-manager :addTextureTask] (str id) url no-mipmap-or-options invert-y)
        (j/assoc! :onSuccess (fn [task]
                               (j/assoc-in! api.core/db [:assets id] (j/get task :texture))
                               (when on-success (on-success (j/get task :texture))))))))

(defn add-mesh-task [id url on-success]
  (-> (j/call-in api.core/db [:assets-manager :addMeshTask] (str id) "" "models/" url)
      (j/assoc! :onSuccess (fn [task]
                             (let [mesh (j/get-in task [:loadedMeshes 0])
                                   loaded-meshes (j/get task :loadedMeshes)
                                   anim-groups (j/get task :loadedAnimationGroups)
                                   loaded-transform-nodes (j/get task :loadedTransformNodes)]
                               (j/assoc-in! api.core/db [:assets id] mesh)
                               (when on-success (on-success mesh anim-groups loaded-meshes loaded-transform-nodes)))))))

(defn add-container-task [id url on-success]
  (-> (j/call-in api.core/db [:assets-manager :addContainerTask] (str id) "" "models/" url)
      (j/assoc! :onSuccess (fn [task]
                             (let [{:keys [loadedContainer
                                           loadedMeshes
                                           loadedAnimationGroups
                                           loadedSkeletons]} (j/lookup task)]
                               (j/assoc-in! api.core/db [:assets id] loadedContainer)
                               (when on-success (on-success loadedContainer
                                                            loadedMeshes
                                                            loadedAnimationGroups
                                                            loadedSkeletons)))))))

(defn add-binary-file-task [id url on-success]
  (-> (j/call-in api.core/db [:assets-manager :addBinaryFileTask] (str id) url)
      (j/assoc! :onSuccess (fn [task]))))

(defn add-text-file-task [id url on-success]
  (-> (j/call-in api.core/db [:assets-manager :addTextFileTask] (str id) url)
      (j/assoc! :onSuccess (fn [task]
                             (when on-success (on-success (j/get task :text)))))))

(defn- create-asset-tasks [asset-regs]
  (doseq [{:keys [id type url on-success]} asset-regs]
    (case type
      :texture (add-texture-task (name id) url on-success)
      :mesh (add-mesh-task (name id) url on-success)
      :container (add-container-task (name id) url on-success)
      :text (add-text-file-task (name id) url on-success)
      #_#_:sound (add-binary-file-task (name id) url type)
      nil)))

(defn create-preload-asset-tasks []
  (create-asset-tasks (filter :preload? (js/Object.values (j/get api.core/db :assets-regs)))))

(defn create-leftover-asset-tasks []
  (create-asset-tasks (remove :preload? (js/Object.values (j/get api.core/db :assets-regs)))))

(defn get-asset [id]
  (if-let [asset (j/get-in api.core/db [:assets id])]
    asset
    (let [asset-regs (j/get api.core/db :assets-regs)]
      (cond
        (= "texture" (namespace id))
        (let [tex (api.core/texture (:url (j/get asset-regs id)) (j/get asset-regs id))]
          (j/assoc-in! api.core/db [:assets id] tex)
          tex)))))

(defn load-async []
  (let [p (a/promise-chan)]
    (-> (j/call-in api.core/db [:assets-manager :loadAsync])
        (j/call :then #(a/put! p true)))
    p))

(comment
  (add-binary-file-task "air" "sound/air_whoosh.wav")
  (load-async)
  (get-asset :material/fire-nova-disallowed)
  )
