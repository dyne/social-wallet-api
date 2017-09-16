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

(defproject social-wallet-api "0.1.0-SNAPSHOT"
  :description "Freecoin web API for wallet operations"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [metosin/compojure-api "1.1.10"]
                 [ring/ring-core "1.6.1"]
                 [ring/ring-defaults "0.3.0"]

                 [org.clojars.dyne/freecoin-lib "0.4.0-SNAPSHOT"]

                 ;; simple secret sharing
                 [org.clojars.dyne/fxc "0.5.0"]

                 ;; for rendering of readme etc.
                 [markdown-clj "0.9.99"]

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
  :deploy-repositories [["releases" {:url :clojars
                                     :creds :gpg}]]
  :ring {:init    social-wallet-api.handler/init
         :handler social-wallet-api.handler/app
         :destroy social-wallet-api.handler/destroy}

  :uberjar-name "server.jar"
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [cheshire "5.7.1"]
                                  [ring/ring-mock "0.3.0"]
                                  [midje "1.8.3"]]
                   :plugins [[lein-ring "0.12.0"]
                             [lein-midje "3.2"]]}})
