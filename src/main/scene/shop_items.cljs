(ns main.scene.shop-items)

(def hats
  [:item/skyband {:name "Skyband"
                  :model "Item_Skyband"
                  :img "Skyband.png"
                  :type :head
                  :rewarded? true
                  :position [0 -0.28 -0.07]
                  :powers {:speed 1}
                  :scale 0.0001}
   :item/willowquill {:name "Willowquill"
                      :model "Item_Willowquill"
                      :img "Willowquill.png"
                      :type :head
                      :price 100
                      :position [0 -0.1 -0.15]
                      :powers {:speed 2
                               :defense 1}
                      :scale 0.0001}
   :item/bluebuckle {:name "Bluebuckle"
                     :model "Item_Bluebuckle"
                     :img "Bluebuckle.png"
                     :type :head
                     :rewarded? true
                     :position [0 -0.1 -0.02]
                     :powers {:defense 1}
                     :scale 0.0001}
   :item/sandslouch {:name "Sandslouch"
                     :model "Item_Sandslouch"
                     :img "Sandslouch.png"
                     :type :head
                     :price 250
                     :powers {:speed 1
                              :defense 2}
                     :position [0 -0.15 -0.03]
                     :scale 0.01}
   :item/stonehood {:name "Stonehood"
                    :model "Item_Stonehood"
                    :img "Stonehood.png"
                    :type :head
                    :price 500
                    :powers {:speed 2
                             :damage 1}
                    :position [0 -0.35 -0.09]
                    :scale [0.00015 0.00012 0.00012]}
   :item/wirltop {:name "Whirltop"
                  :model "Item_Whirltop"
                  :img "Whirltop.png"
                  :type :head
                  :price 5000
                  :position [0.03 -0.05 -0.0]
                  :powers {:speed 4
                           :ice-immune 2
                           :damage 4
                           :defense 2}
                  :scale 0.0001}
   :item/crestforge {:name "Crestforge"
                     :model "Item_Crestforge"
                     :img "Crestforge.png"
                     :type :head
                     :price 100000
                     :position [0 -0.17 -0.03]
                     :powers {:speed 6
                              :stun 2
                              :ice-immune 1
                              :light-immune 1
                              :damage 5
                              :defense 6}
                     :scale 0.0001}
   :item/shadowcarve {:name "Shadowcarve"
                      :model "Item_Shadowcarve"
                      :img "Shadowcarve.png"
                      :type :head
                      :price 10000
                      :position [-0.01 -0.35 0.08]
                      :powers {:speed 5
                               :light-immune 2
                               :damage 4
                               :defense 5}
                      :scale 0.00012}
   :item/bearmaw {:name "Bearmaw"
                  :model "Item_Bearmaw"
                  :img "Bearmaw.png"
                  :type :head
                  :price 2000
                  :position [0 -0.3 0]
                  :powers {:speed 3
                           :wind-immune 1
                           :damage 2
                           :defense 3}
                  :scale 0.0001}
   :item/duskfold {:name "Duskfold"
                   :model "Item_Duskfold"
                   :img "Duskfold.png"
                   :type :head
                   :rewarded? true
                   :position [0 -0.3 -0.05]
                   :powers {:damage 1}
                   :rotation-y 180
                   :scale 0.00015}
   :item/highcap {:name "Highcap"
                  :model "Item_Highcap"
                  :img "Highcap.png"
                  :type :head
                  :rewarded? true
                  :position [0 -0.32 -0.1]
                  :powers {:fire-immune 1}
                  :scale [0.00014 0.00012 0.00012]}
   :item/ironmaw {:name "Ironmaw"
                  :model "Item_Ironmaw"
                  :img "Ironmaw.png"
                  :type :head
                  :price 1500
                  :position [0 -0.28 -0.07]
                  :powers {:speed 2
                           :ice-immune 1
                           :damage 1
                           :defense 3}
                  :scale [0.00015 0.00012 0.00012]}
   :item/nightwrap {:name "Nightwrap"
                    :model "Item_Nightwrap"
                    :img "Nightwrap.png"
                    :type :head
                    :rewarded? true
                    :position [0 -0.33 -0.07]
                    :powers {:ice-immune 1}
                    :scale [0.00013 0.00013 0.0001]}
   :item/plainsquill {:name "Plainsquill"
                      :model "Item_Plainsquill"
                      :img "Plainsquill.png"
                      :type :head
                      :price 1200
                      :position [0 -0.1 -0.02]
                      :powers {:speed 2
                               :fire-immune 1
                               :damage 1}
                      :scale 0.0001}
   :item/plumepeak {:name "Plumepeak"
                    :model "Item_Plumepeak"
                    :img "Plumepeak.png"
                    :type :head
                    :price 900
                    :position [0.03 -0.02 -0.15]
                    :powers {:speed 1
                             :defense 1
                             :damage 1}
                    :scale 0.0001}
   :item/silverband {:name "Silverband"
                     :model "Item_Silverband"
                     :img "Silverband.png"
                     :type :head
                     :price 750
                     :powers {:speed 2
                              :defense 2}
                     :position [0 -0.35 -0.1]
                     :scale [0.00015 0.00012 0.00012]}
   :item/tuskmask {:name "Tuskmask"
                   :model "Item_Tuskmask"
                   :img "Tuskmask.png"
                   :type :head
                   :rewarded? true
                   :powers {:wind-immune 1}
                   :position [0 -0.3 -0.03]
                   :scale [0.00016 0.00012 0.00012]}
   :item/wolfcry {:name "Wolfcry"
                  :model "Item_Wolfcry"
                  :img "Wolfcry.png"
                  :type :head
                  :price 3000
                  :position [0 -0.25 0]
                  :powers {:speed 4
                           :ice-immune 1
                           :damage 3
                           :defense 3}
                  :scale 0.00011}])

(def capes
  [:item/aurora {:name "Aurora"
                 :model "Item_Aurora"
                 :img "Aurora.png"
                 :type :cape
                 :rewarded? true
                 :position [0 -0.42 -0.29]
                 :rotation-x 74
                 :rotation-y -180
                 :rotation-z -180
                 :powers {:speed 1}
                 :scale 0.0001}
   :item/azurefall {:name "Azurefall"
                    :model "Item_Azurefall"
                    :img "Azurefall.png"
                    :type :cape
                    :price 500
                    :position [0 -0.55 -0.39]
                    :rotation-x 74
                    :rotation-y -180
                    :rotation-z -180
                    :powers {:speed 2
                             :defense 1}
                    :scale 0.0001}
   :item/bearhide-cloak {:name "Bearhide Cloak"
                         :model "item_Bearhide_Cloak"
                         :img "Bearhide_Cloak.png"
                         :type :cape
                         :price 1000
                         :position [0 -0.45 -0.34]
                         :rotation-x 74
                         :rotation-y -180
                         :rotation-z -180
                         :powers {:speed 2
                                  :damage 1
                                  :wind-immune 1
                                  :defense 1}
                         :scale 0.0001}
   :item/bloodweave {:name "Bloodweave"
                     :model "Item_Bloodweave"
                     :img "Bloodweave.png"
                     :type :cape
                     :price 750
                     :position [0 -0.6 -0.4]
                     :rotation-x 74
                     :rotation-y -180
                     :rotation-z -180
                     :powers {:speed 1
                              :damage 1
                              :defense 1}
                     :scale 0.0001}
   :item/emerald {:name "Emerald"
                  :model "Item_Emerald"
                  :img "Emerald.png"
                  :type :cape
                  :price 5000
                  :position [0 -0.51 -0.38]
                  :rotation-x 74
                  :rotation-y -180
                  :rotation-z -180
                  :powers {:speed 2
                           :damage 2
                           :wind-immune 1
                           :light-immune 1
                           :defense 2}
                  :scale 0.0001}
   :item/mooon-shard {:name "Mooon Shard"
                      :model "Item_Moon_Shard"
                      :img "Moon_Shard.png"
                      :type :cape
                      :price 250
                      :position [0 -0.58 -0.42]
                      :rotation-x 74
                      :rotation-y -180
                      :rotation-z -180
                      :powers {:speed 2
                               :damage 1}
                      :scale 0.0001}
   :item/mystic-robe {:name "Mystic Robe"
                      :model "Item_Mystic_Robe"
                      :img "Mystic_Robe.png"
                      :type :cape
                      :price 10000
                      :position [0 -0.65 -0.41]
                      :rotation-x 74
                      :rotation-y -180
                      :rotation-z -180
                      :powers {:speed 3
                               :damage 2
                               :wind-immune 1
                               :ice-immune 2
                               :defense 2}
                      :scale 0.0001}
   :item/sand-mantle {:name "Sand Mantle"
                      :model "Item_Sand_Mantle"
                      :img "Sand_Mantle.png"
                      :type :cape
                      :rewarded? true
                      :powers {:damage 1}
                      :position [0 -0.57 -0.36]
                      :rotation-x 74
                      :rotation-y -180
                      :rotation-z -180
                      :scale 0.0001}
   :item/shadow-wrap {:name "Shadow Wrap"
                      :model "Item_Shadow_Wrap"
                      :img "Shadow_Wrap.png"
                      :type :cape
                      :price 100000
                      :position [0 -0.57 -0.36]
                      :rotation-x 74
                      :rotation-y -180
                      :rotation-z -180
                      :powers {:speed 4
                               :damage 2
                               :wind-immune 1
                               :light-immune 1
                               :ice-immune 2
                               :defense 3}
                      :scale 0.0001}
   :item/spirit {:name "Spirit"
                 :model "Item_Spirit"
                 :img "Spirit.png"
                 :type :cape
                 :rewarded? true
                 :rotation-x 74
                 :rotation-y -180
                 :rotation-z -180
                 :powers {:fire-immune 1}
                 :position [0 -0.31 -0.296]
                 :scale 0.0001}
   :item/sunflare {:name "Sunflare"
                   :model "Item_Sunflare"
                   :img "Sunflare.png"
                   :type :cape
                   :rewarded? true
                   :powers {:defense 1}
                   :position [0 -0.55 -0.34]
                   :rotation-x 74
                   :rotation-y -180
                   :rotation-z -180
                   :scale 0.0001}])

(def attachments
  [:item/beltflask {:name "Beltflask"
                    :model "Item_Beltflask"
                    :img "Beltflask.png"
                    :type :attachment
                    :price 100
                    :position [0.31 0 0.21]
                    :powers {:speed 2
                             :damage 1}
                    :rotation-x 73
                    :rotation-y 39
                    :rotation-z 81
                    :scale 0.0001}
   :item/brewflask {:name "Brewflask"
                    :model "Item_Brewflask"
                    :img "Brewflask.png"
                    :type :attachment
                    :rewarded? true
                    :position [0.3 -0.01 0.2]
                    :powers {:speed 1}
                    :rotation-y -40.8
                    :rotation-z 10
                    :scale 0.0001}
   :item/brewvial {:name "Brewvial"
                   :model "Item_Brewvial"
                   :img "Brewvial.png"
                   :type :attachment
                   :rewarded? true
                   :position [0.3 -0.01 0.25]
                   :powers {:defense 1}
                   :rotation-y -40.8
                   :rotation-z 10
                   :scale 0.00008}
   :item/bucklepouch {:name "Bucklepouch"
                      :model "Item_Bucklepouch"
                      :img "Bucklepouch.png"
                      :type :attachment
                      :price 500
                      :position [0.3 -0.01 0.25]
                      :powers {:speed 2
                               :damage 1
                               :defense 2
                               :wind-immune 1}
                      :rotation-y 120
                      :scale 0.0001}
   :item/claybottle {:name "Claybottle"
                     :model "Item_Claybottle"
                     :img "Claybottle.png"
                     :type :attachment
                     :price 250
                     :position [0.3 0.05 0.25]
                     :powers {:speed 1
                              :damage 1
                              :defense 1}
                     :rotation-y 120
                     :scale 0.00005}
   :item/dustsack {:name "Dustsack"
                   :model "Item_Dustsack"
                   :img "Dustsack.png"
                   :type :attachment
                   :price 1000
                   :position [0.3 0 0.2]
                   :powers {:speed 3
                            :damage 1
                            :defense 1
                            :light-immune 1}
                   :scale 0.0001}
   :item/flatcase {:name "Flatcase"
                   :model "Item_Flatcase"
                   :img "Flatcase.png"
                   :type :attachment
                   :price 300
                   :position [0.3 0 0.25]
                   :powers {:speed 2
                            :damage 1
                            :fire-immune 1}
                   :rotation-y -40
                   :scale 0.00008}
   :item/hornsling {:name "Hornsling"
                    :model "Item_Hornsling"
                    :img "Hornsling.png"
                    :type :attachment
                    :price 1500
                    :position [0.352 -0.118 0.31]
                    :powers {:speed 2
                             :damage 2
                             :defense 2
                             :light-immune 1
                             :fire-immune 1}
                    :rotation-x 111.8
                    :rotation-y 288.4
                    :scale 0.0001}
   :item/loopcoil {:name "Loopcoil"
                   :model "Item_Loopcoil"
                   :img "Loopcoil.png"
                   :type :attachment
                   :rewarded? true
                   :position [0.352 -0.129 0.292]
                   :powers {:damage 1}
                   :rotation-x 50.72
                   :rotation-y 47.67
                   :rotation-z 126
                   :scale 0.0001}
   :item/magic-scroll {:name "Magic Scroll"
                       :model "Item_Magic_Scroll"
                       :img "Magic_Scroll.png"
                       :type :attachment
                       :price 5000
                       :position [0.28 0 0.22]
                       :powers {:speed 2
                                :damage 3
                                :defense 4
                                :stun 2
                                :fire-immune 2
                                :light-immune 2}
                       :rotation-x 58
                       :rotation-y 11
                       :rotation-z 68
                       :scale 0.00009}
   :item/sidepouch {:name "Sidepouch"
                    :model "Item_Sidepouch"
                    :img "Sidepouch.png"
                    :type :attachment
                    :rewarded? true
                    :position [0.32 0 0.22]
                    :powers {:stun 1}
                    :rotation-x 73
                    :rotation-y 30
                    :rotation-z 36
                    :scale 0.0001}
   :item/stashbox {:name "Stashbox"
                   :model "Item_Stashbox"
                   :img "Stashbox.png"
                   :type :attachment
                   :price 2500
                   :position [0.32 0 0.22]
                   :powers {:speed 2
                            :damage 2
                            :defense 2
                            :stun 1
                            :fire-immune 2}
                   :rotation-x 73
                   :rotation-y 30
                   :rotation-z -105
                   :scale 0.0001}])

;; TODO add Levels
(def all-items
  (apply array-map (concat hats capes attachments)))
