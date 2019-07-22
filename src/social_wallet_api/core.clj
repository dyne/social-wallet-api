;; Social Wallet REST API

;; Copyright (C) 2019- Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Aspasia Beneti <aspra@dyne.org>

;; This file is part of Social Wallet REST API.

;; Social Wallet REST API is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

;; Social Wallet REST API is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.

;; Additional permission under GNU AGPL version 3 section 7.

;; If you modify Social Wallet REST API, or any covered work, by linking or combining it with any library (or a modified version of that library), containing parts covered by the terms of EPL v 1.0, the licensors of this Program grant you additional permission to convey the resulting work. Your modified version must prominently offer all users interacting with it remotely through a computer network (if your version supports such interaction) an opportunity to receive the Corresponding Source of your version by providing access to the Corresponding Source from a network server at no charge, through some standard or customary means of facilitating copying of software. Corresponding Source for a non-source form of such a combination shall include the source code for the parts of the libraries (dependencies) covered by the terms of EPL v 1.0 used as well as that of the covered work.

(ns social-wallet-api.core
  (:require [schema.core :as s]

            [taoensso.timbre :as log]

            [auxiliary.config :refer [config-read]]
            [freecoin-lib.core :as lib]
            [freecoin-lib.sawtooth :as lib-saw]
            [freecoin-lib.app :as freecoin]
            
            [failjure.core :as f]
            
            [social-wallet-api.api-key :refer [create-and-store-apikey! fetch-apikey apikey
                                               write-apikey-file]]
            [social-wallet-api.schema :refer [Config]]))

(def available-blockchains #{:faircoin :bitcoin :litecoin :multichain :sawtooth})

(defonce prod-app-name "social-wallet-api")
(defonce config-default (config-read prod-app-name))

(defonce connections (atom {}))
(defonce client (atom nil))

;; TODO: lets see why we need this
(defn- get-config
  "sanitize configuration or returns nil if not found"
  [obj]  
  (if (contains? obj :config)
    (let [mc (merge config-default {:defaults (:config obj)})]
      ;; any imposed conversion of config values may happen here
      ;; for example values that must be integer or strings:
      ;; (merge mc {:total  (Integer. (:total mc))
      ;;            :quorum (Integer. (:quorum mc))})
      mc
      )
    nil))

;; generic wrapper to complete the conf structure if missing
;; TODO: may be a good point to insert promises and raise errors
;; WHat is this for?
(defn- complete [func obj]
  (if-let [conf (get-config obj)]
    {:data (func conf (:data obj))
     :config conf}
    {:data (func config-default (:data obj))
     :config config-default}))

(defn- get-connection-configs [config app-name]
  (get-in config [(keyword app-name) :freecoin]))

(defn- get-app-conf [config app-name]
  (get-in config [(keyword app-name) :freecoin]))

(defn- blockchain-conf->conn [blockchain blockchain-conf]
  (case blockchain
    :sawtooth  (lib-saw/new-sawtooth (:currency blockchain-conf)
                                     (select-keys blockchain-conf [:host]))
    (merge (lib/new-btc-rpc (:currency blockchain-conf) 
                            (:rpc-config-path blockchain-conf))
           {:confirmations {:number-confirmations (:number-confirmations blockchain-conf)
                            :frequency-confirmations-millis (:frequency-confirmations-millis blockchain-conf)}}
           {:deposits {:deposit-expiration-millis (:deposit-expiration-millis blockchain-conf)
                       :frequency-deposit-millis (:frequency-deposit-millis blockchain-conf)}})))

(s/defn ^:always-validate init
  ([]
   (init config-default prod-app-name))
  ([config :- Config app-name] 
   (log/debug "Initialising app with name: " app-name)
   ;; TODO: this should be able to read from resources or a specific file path
   (when-let [log-level (get-in config [(keyword app-name) :log-level])]
     (log/merge-config! {:level (keyword log-level)
                         ;; #{:trace :debug :info :warn :error :fatal :report}

                         ;; Control log filtering by
                         ;; namespaces/patterns. Useful for turning off
                         ;; logging in noisy libraries, etc.:
                         :ns-whitelist  ["social-wallet-api.*"
                                         "freecoin-lib.*"
                                         "clj-storage.*"]
                         :ns-blacklist  ["org.eclipse.jetty.*"]}))

   ;; TODO a more generic way to go multiple configurations
   (let [swapi-conf (get-app-conf config app-name) 
         mongo (lib/new-mongo (->  (log/spy swapi-conf) :mongo :currency)
                              (freecoin/connect-mongo (dissoc swapi-conf :currency)))]
     (swap! connections conj {:mongo mongo})
     (log/warn "MongoDB backend connected.")

     ;; Setup API KEY when needed
     (let [apikey-store (-> @connections :mongo :stores-m :apikey-store)]
       (when-let [client-app (:apikey swapi-conf)]
         (reset! client client-app)
         (reset! apikey (apply hash-map (vals
                                         (or (fetch-apikey apikey-store client-app)
                                             (create-and-store-apikey! apikey-store client-app 32)))))
         (write-apikey-file "apikey.yaml" (str client-app ":\n " (get @apikey client-app))))))

   ;; Read all blockchain configs and start connections
   (doseq [[blockchain blockchain-conf] (select-keys (get-connection-configs config app-name) available-blockchains)]
     (f/if-let-ok? [blockchain-conn (blockchain-conf->conn blockchain blockchain-conf)]
       ;; TODO add schema fair
       (do
         (swap! connections conj {blockchain blockchain-conn})
         (log/info (str (name blockchain) " config is loaded")))
       (log/error (f/message blockchain-conn))))))

(defn destroy []
  (log/warn "Stopping the Social Wallet API.")
  (freecoin/disconnect-mongo (:mongo @connections))
  (reset! client "")
  (reset! apikey {}))
