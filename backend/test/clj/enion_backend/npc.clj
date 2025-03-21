(ns enion-backend.npc
  (:require
    [clojure.test :refer :all]
    [enion-backend.npc :as npc]))

;; Tests
#_(deftest fsm-state-transitions-test
  (testing "FSM state transitions"
    (let [initial-states [:idle :attack :chase :change-pos :die]
          scenarios [{:label "not-alive"
                      :alive? false
                      :expected-state :die}
                     {:label "attack-player"
                      :alive? true
                      :should-attack? true
                      :player-close? true
                      :expected-state :attack}
                     {:label "chase-player"
                      :alive? true
                      :should-attack? true
                      :player-close? false
                      :expected-state :chase}
                     {:label "change-pos"
                      :alive? true
                      :should-attack? false
                      :change-pos? true
                      :expected-state :change-pos}
                     {:label "idle"
                      :alive? true
                      :should-attack? false
                      :change-pos? false
                      :expected-state :idle}]]
      (doseq [initial-state initial-states
              scenario scenarios]
        (testing (str "Initial state: " (name initial-state) ", Scenario: " (:label scenario))
          (let [test-npc (-> {:id 1 :name "test" :pos [0 0] :health 100 :cooldown 100}
                             (assoc :state (npc/states initial-state)))]
            (with-redefs [npc/alive? (constantly (:alive? scenario))
                          npc/get-player-id-to-attack (constantly (:should-attack? scenario))
                          npc/player-close-for-attack? (constantly (:player-close? scenario))
                          npc/need-to-change-pos? (constantly (:change-pos? scenario))]
              (let [new-npc (-> test-npc
                                npc/update-npc!
                                npc/update-npc!)
                    new-state (:name (:state new-npc))]
                (is (= (:expected-state scenario) new-state)
                    (str "Expected " (:expected-state scenario) " state, but got " new-state))))))))))
