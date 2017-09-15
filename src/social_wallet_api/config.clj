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

(ns social-wallet-api.config
  (:require [clojure.java.io :as io]
            [schema.core :as s]
            [ring.swagger.json-schema :as rjs]
            [cheshire.core :refer :all]))

(def config-default {;; Freecoin-lib
                     :blockchain "Mongo"
                     :api-id  "anon"
                     :api-key "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                     :rpc-user "anon"
                     :rpc-pass "secret"
                     :rpc-port 8333
                     :rpc-host "localhost"
                     })

(defn- k [type key default]
  (rjs/field type {:example (get default key)}))

(def config-scheme
  {
   (s/optional-key :blockchain) (k s/Str :blockchain config-default)
   (s/optional-key :api-id) (k s/Str :api-id config-default)
   (s/optional-key :api-key) (k s/Str :api-key config-default)
   (s/optional-key :rpc-user) (k s/Str :rpc-user config-default)
   (s/optional-key :rpc-pass) (k s/Str :rpc-pass config-default)
   (s/optional-key :rpc-port) (k s/Str :rpc-port config-default)
   (s/optional-key :rpc-host) (k s/Str :rpc-host config-default)
   })

(s/defschema Config
  {(s/required-key :config) config-scheme})

(defn config-read
  "read configurations from standard locations, overriding defaults or
  system-wide with user specific paths."
  ([] (config-read config-default))
  ([default]
   (let [home (System/getenv "HOME")
         pwd  (System/getenv "PWD" )]
     (loop [[p & paths] ["/etc/fxc/config.json"
                         (str home "/.fxc/config.json")
                         (str pwd "/config.json")]
            res default]
       (let [res (merge res
                        (if (.exists (io/as-file p))
                          (conj {:config p} (parse-stream (io/reader p) true))))]
         (if (empty? paths) (conj {:config false} res)
             (recur paths res)))))))

(defn config-write
  "write configurations to file"
  [conf file]
  (generate-stream conf (io/writer file)
                   {:pretty true}))
