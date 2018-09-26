;; Social Wallet REST API

;; Copyright (C) 2017 Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Denis Roio <jaromil@dyne.org>
;; Aspasia Beneti <aspra@dyne.org>

;; This program is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; This program is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with this program.  If not, see <http://www.gnu.org/licenses/>.

(def version (clojure.string/trim (slurp "VERSION")))

(defproject social-wallet-api version
  :description "Freecoin web API for wallet operations"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [metosin/compojure-api "2.0.0-alpha17"]
                 [ring/ring-core "1.7.0"]
                 [ring/ring-defaults "0.3.2"]

                 ;; core freecoin toolkit library
                 [org.clojars.dyne/freecoin-lib ~version]
                 
                 ;; auxiliary functions (configuration etc.)
                 [org.clojars.dyne/auxiliary "0.4.0"]

                 ;; for rendering of readme etc.
                 [markdown-clj "1.0.2"]

                 ;; error handling
                 [failjure "1.3.0"]
                 
                 ;; logging done right with timbre and slf4j
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]

                 ;; Convinence fns for better control flow
                 [dom-top "1.0.3"]

                 ;; Use mongo bson data types like Decimal128
                 [org.mongodb/mongodb-driver "3.8.2"]]

  :jvm-opts ["-Djava.security.egd=file:/dev/random" ;use a proper random source
             "-XX:-OmitStackTraceInFastThrow" ; stacktrace JVM exceptions
             ]
  :license {:author "Denis Roio"
            :email "jaromil@dyne.org"
            :year 2017
            :key "gpl-3.0"}

  :resource-paths ["resources" "test-resources"]
  :deploy-repositories [["releases" {:url :clojars
                                     :creds :gpg}]]
  :ring {:init    social-wallet-api.handler/init
         :handler social-wallet-api.handler/app
         ;; Accessible only from localhost
         ;; https://stackoverflow.com/questions/24467539/lein-ring-server-headless-only-listen-to-localhost
         :host ~(or (System/getenv "SWAPI_HOST") "localhost")
         :destroy social-wallet-api.handler/destroy
         :reload-paths ["src"]}

  :uberjar-name "social-wallet-api.jar"
  :alias {"test" ["midje"]}
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.2"]
                                  ;; this is necessary to use the for-all midje support
                                  [midje "1.9.2"]
                                  ;; json
                                  [cheshire "5.8.1"]
                                  ;; generative testing
                                  [org.clojure/test.check "0.10.0-alpha2"]]
                   :repl-options {:init-ns social-wallet-api.handler}
                   :plugins [[lein-ring "0.12.0"]
                             [lein-midje "3.2"]]}})
 
