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

(ns social-wallet-api.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [ring.middleware.defaults :refer
             [wrap-defaults site-defaults]]
            [ring.middleware.session :refer :all]
            [markdown.core :as md]

            [taoensso.timbre :as log]

            [auxiliary.config :refer [config-read]]
            [freecoin-lib.core :as lib]
            [freecoin-lib.app :as freecoin]
            [social-wallet-api.schemas :refer [Query Tag Transaction
                                               Address]]))

(def  app-name "social-wallet-api")
(defonce config-default (config-read app-name))

(defonce blockchains (atom {}))

(defn- get-config [obj]
  "sanitize configuration or returns nil if not found"
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
(defn- complete [func obj schema]
  (if-let [conf (get-config obj)]
    {:data (func conf (:data obj))
     :config conf}
    {:data (func config-default (:data obj))
     :config config-default}))

;; TODO: pass blockchains as arg?
(defn- get-blockchain [blockchains query]
  (log/spy (get @blockchains (-> query :blockchain keyword))))

(defn- get-blockchain-conf [config app-name blockchain]
  (get-in config [(keyword app-name) :freecoin blockchain]))

(defn init
  ([]
   (init config-default app-name))
  ([config app-name]
   ;; TODO: this should be able to read from resources or a specific file path
   (if-let [log-level (get-in config [(keyword app-name) :log-level])]
     (log/merge-config! {:level (keyword log-level)
                         ;; #{:trace :debug :info :warn :error :fatal :report}

                         ;; Control log filtering by
                         ;; namespaces/patterns. Useful for turning off
                         ;; logging in noisy libraries, etc.:
                         :ns-whitelist  ["social-wallet-api.*"]
                         :ns-blacklist  ["org.eclipse.jetty.*"]}))

   ;; TODO a more generic way to go multiple configurations
   (let [mongo (->> (get-blockchain-conf config app-name :mongo)
                    freecoin/connect-mongo lib/new-mongo)]
     (swap! blockchains conj {:mongo mongo})
     (log/warn "MongoDB backend connected."))
   (let [fair-conf (get-blockchain-conf config app-name :faircoin)
         fair (lib/new-btc-rpc (:currency fair-conf) (:rpc-config-path fair-conf))]
     (swap! blockchains conj {:faircoin fair})
     (log/warn "Faircoin config is loaded"))))

(defn destroy []
  (log/warn "Stopping the Social Wallet API.")
  (freecoin/disconnect-mongo (:mongo @blockchains))
  ;; TODO: fair?
  )

(def rest-api
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info
             {:version (clojure.string/trim (slurp "VERSION"))
              :title "Social-wallet-api"
              :description "Social Wallet REST API backend for webapps"
              :contact {:url "https://github.com/pienews/social-wallet-api"}}}}}

    (context "/" []
             :tags ["INFO"]
             (GET "/readme" request
                  {:headers {"Content-Type"
                             "text/html; charset=utf-8"}
                   :body (md/md-to-html-string
                          (slurp "README.md"))}))

    (context "/" []
             :tags ["LABEL"]
             (POST "/label" request
                   :return s/Keyword
                   :body [query Query]
                   :summary "Show the blockchain label"
                   :description "

Takes a JSON structure made of a `blockchain` identifier.

It returns the label value.

"
                   (if-let [blockchain (log/spy (get-blockchain blockchains (log/spy query)))]
                     (ok (lib/label blockchain))
                     (not-found "The blockchain conf is not loaded."))))

    (context "/wallet/v1/address" []
             :tags ["ADDRESS"]
             (POST "/address" request
                  :return [Address]
                  :body [query Query]
                  :summary "List all addresses related to an account"
                  :description "

Takes a JSON structure made of a `blockchain` identifier and an `account id`.

It returns a list of addresses for the particular account.

"
                  (ok (lib/get-address
                       (get-blockchain blockchains query)
                       {}))))
    
    (context "/wallet/v1/tags" []
             :tags ["TAGS"]
             (POST "/list" request
                  :return [Tag]
                  :body [query Query]
                  :summary "List all tags"
                  :description "

Takes a JSON structure made of a `blockchain` identifier.

It returns a list of tags found on that blockchain.

"
                  (ok (lib/list-tags
                       (get-blockchain blockchains query)
                       {}))))

    (context "/wallet/v1/transactions" []
             :tags ["TRANSACTIONS"]
             (POST "/list" request
                   :return [Transaction]
                   :body [query Query]
                   :summary "List all transactions"
                   :description "
Takes a JSON structure with a `blockchain` query identifier.

Returns a list of transactions found on that blockchain.

"
                   (ok (lib/list-transactions
                        (get-blockchain blockchains query)
                        {}))))

    ;; (context "/wallet/v1/accounts" []
    ;;          :tags ["ACCOUNTS"]
    ;;          (GET "/list" request
    ;;               {:return Accounts
    ;;                :summary "List all valid accounts"
    ;;                :body (ok {:data (list-accounts (:backend @app-state))})}))

    ;;          (POST "import" []
    ;;                :return Accounts
    ;;                :body [account Accounts]
    ;;                :summary "Import an existing account"
    ;;                (ok (complete import-account account Accounts)))

    ;;          (POST "create" []
    ;;                :return Accounts
    ;;                :body [account Accounts]
    ;;                :summary "Create a new account"
    ;;                (ok (complete create-account account Accounts))))

    ))

;; explicit middleware configuration for compojure
(def rest-api-defaults
  "A default configuration for a browser-accessible website that's accessed
  securely over HTTPS."
  (-> site-defaults
      (assoc-in [:cookies] false)
      (assoc-in [:security :anti-forgery] false)
      (assoc-in [:security :ssl-redirect] false)
      (assoc-in [:security :hsts] true)))

(def app
  (wrap-defaults rest-api rest-api-defaults))
