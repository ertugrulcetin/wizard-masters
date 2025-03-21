(defproject enion-backend "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[aleph "0.8.1"]
                 [amalloy/ring-buffer "1.3.1"]
                 [amalloy/ring-gzip-middleware "0.1.4"]
                 [ch.qos.logback/logback-classic "1.4.4"]
                 [clojure.java-time "1.1.0"]
                 [clojure-msgpack "1.2.1"]
                 [com.taoensso/carmine "3.4.1"]
                 [cprop "0.1.19"]
                 [io.sentry/sentry-clj "6.13.191"]
                 [luminus-aleph "0.2.0"]
                 [luminus/ring-ttl-session "0.3.3"]
                 [luminus-transit "0.1.5"]
                 [metosin/muuntaja "0.6.8"]
                 [metosin/reitit "0.5.18"]
                 [metosin/ring-http-response "0.9.3"]
                 [mount "0.1.16"]
                 [nano-id "1.0.0"]
                 [nrepl "1.0.0"]
                 [funcool/promesa "9.0.471"]
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/tools.cli "1.0.214"]
                 [org.clojure/tools.logging "1.2.4"]
                 [ring-cors/ring-cors "0.1.13"]
                 [ring/ring-core "1.9.6"]
                 [ring/ring-defaults "0.3.4"]
                 [org.clojure/core.async "1.7.701"]
                 [selmer "1.12.55"]
                 [weavejester/dependency "0.2.1"]
                 [lurodrigo/firestore-clj "1.2.1" :exclusions [com.google.errorprone/error_prone_annotations]]
                 [kezban "0.1.94"]
                 [clj-http "3.12.3"]
                 [cheshire "5.11.0"]
                 [buddy/buddy-sign "3.4.333"]]

  :min-lein-version "2.0.0"

  :source-paths ["src/clj" "../common/src"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main ^:skip-aot enion-backend.core

  :plugins []

  :profiles
  {:uberjar {:omit-source true
             :aot :all
             :uberjar-name "enion-backend.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev [:project/dev :profiles/dev]
   :test [:project/dev :project/test :profiles/test]

   :project/dev {:jvm-opts ["-Dconf=dev-config.edn"]
                 :dependencies [[org.clojure/tools.namespace "1.3.0"]
                                [pjstadig/humane-test-output "0.11.0"]
                                [prone "2021-04-23"]
                                [ring/ring-devel "1.9.6"]
                                [ring/ring-mock "0.4.0"]]
                 :plugins [[com.jakemccrary/lein-test-refresh "0.24.1"]
                           [jonase/eastwood "1.2.4"]
                           [cider/cider-nrepl "0.26.0"]]

                 :source-paths ["env/dev/clj"]
                 :resource-paths ["env/dev/resources"]
                 :repl-options {:init-ns user
                                :init (start)
                                :timeout 120000}
                 :injections [(require 'pjstadig.humane-test-output)
                              (pjstadig.humane-test-output/activate!)]}
   :project/test {:jvm-opts ["-Dconf=test-config.edn"]
                  :resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
