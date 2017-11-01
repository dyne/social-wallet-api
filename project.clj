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
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.1.11"]
                 [ring/ring-core "1.6.2"]
                 [ring/ring-defaults "0.3.1"]

                 ;; core freecoin toolkit library
                 [org.clojars.dyne/freecoin-lib ~version]
                 
                 ;; auxiliary functions (configuration etc.)
                 [org.clojars.dyne/auxiliary "0.3.0-SNAPSHOT"]

                 ;; for rendering of readme etc.
                 [markdown-clj "1.0.1"]

                 ;; error handling
                 [failjure "1.2.0"]
                 
                 ;; logging done right with timbre and slf4j
                 [com.taoensso/timbre "4.10.0"]
                 [com.fzakaria/slf4j-timbre "0.3.7"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/log4j-over-slf4j "1.7.25"]
                 [org.slf4j/jul-to-slf4j "1.7.25"]
                 [org.slf4j/jcl-over-slf4j "1.7.25"]]

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
         :destroy social-wallet-api.handler/destroy
         :reload-paths ["src"]}

  :uberjar-name "social-wallet-api.jar"
  :profiles {:dev {:dependencies [[ring/ring-mock "0.3.1"]
                                  [midje "1.8.3"]
                                  ;; json
                                  [cheshire "5.8.0"]]
                   :repl-options {:init-ns social-wallet-api.handler}
                   :plugins [[lein-ring "0.12.0"]
                             [lein-midje "3.2"]]}})
 
