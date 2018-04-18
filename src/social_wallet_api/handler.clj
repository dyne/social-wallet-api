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
            [ring.util.http-status :as status]
            [schema.core :as s]
            [ring.middleware.defaults :refer
             [wrap-defaults site-defaults]]
            [ring.middleware.session :refer :all]
            [markdown.core :as md]

            [taoensso.timbre :as log]

            [auxiliary.config :refer [config-read]]
            [freecoin-lib.core :as lib]
            [freecoin-lib.utils :as lib-utils]
            [freecoin-lib.app :as freecoin]
            [social-wallet-api.schema :refer [Query Tags DBTransaction BTCTransaction TransactionQuery
                                              Addresses Balance PerAccountQuery NewTransactionQuery Label NewDeposit
                                              ListTransactionsQuery MaybeAccountQuery DecodedRawTransaction NewWithdraw
                                              Config DepositCheck AddressNew]]
            [failjure.core :as f]
            [simple-time.core :as time]
            [dom-top.core :as dom]))

(defonce prod-app-name "social-wallet-api")
(defonce config-default (config-read prod-app-name))

(defonce blockchains (atom {}))

;; TODO: lets see why we need this
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
;; WHat is this for?
(defn- complete [func obj schema]
  (if-let [conf (get-config obj)]
    {:data (func conf (:data obj))
     :config conf}
    {:data (func config-default (:data obj))
     :config config-default}))

;; TODO: pass blockchains as arg?
(defn- get-blockchain [blockchains query]
  (get @blockchains (-> query :blockchain keyword)))

(defn- get-db-blockchain [blockchains]
  (:mongo @blockchains))

(defn- get-blockchain-conf [config app-name blockchain]
  (get-in config [(keyword app-name) :freecoin blockchain]))

(defn- get-app-conf [config app-name]
  (get-in config [(keyword app-name) :freecoin]))

(defn- number-confirmations [blockchain transaction-id]
  (-> (lib/get-transaction blockchain transaction-id)
      (get "confirmations")))

(defn- path-with-version [path]
  (str "/wallet/v1" path))

(s/defn ^:always-validate init
  ([]
   (init config-default prod-app-name))
  ([config :- Config app-name] 
   (log/debug "Initialising app with name: " app-name)
   ;; TODO: this should be able to read from resources or a specific file path
   (if-let [log-level (get-in config [(keyword app-name) :log-level])]
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
   (let [mongo (->> (get-app-conf config app-name)
                    freecoin/connect-mongo lib/new-mongo)]
     (swap! blockchains conj {:mongo mongo})
     (log/warn "MongoDB backend connected."))

   (when-let [fair-conf (get-blockchain-conf config app-name :faircoin)]
     (f/if-let-ok? [fair (merge (lib/new-btc-rpc (:currency fair-conf) 
                                                 (:rpc-config-path fair-conf))
                                {:confirmations {:number-confirmations (:number-confirmations fair-conf)
                                                 :frequency-confirmations-millis (:frequency-confirmations-millis fair-conf)}}
                                {:deposits {:deposit-expiration-millis (:deposit-expiration-millis fair-conf)
                                            :frequency-deposit-millis (:frequency-deposit-millis fair-conf)}})]
       ;; TODO add schema fair
       (do
         (swap! blockchains conj {:faircoin fair})
         (log/info "Faircoin config is loaded"))
       (log/error (f/message fair))))))

(defn destroy []
  (log/warn "Stopping the Social Wallet API.")
  (freecoin/disconnect-mongo (:mongo @blockchains))
  ;; TODO: fair?
  )

(defn- with-error-responses [blockchains query ok-fn]
  (try
    (if-let [blockchain (get-blockchain blockchains query)]
      (f/if-let-ok? [response (ok-fn blockchain query)]
        (ok response)
        (bad-request {:error (f/message response)}))
      (not-found {:error "No such blockchain can be found."}))
    (catch java.net.ConnectException e
      (service-unavailable {:error "There was a connection problem with the blockchain."}))))


(defn- blockchain-deposit->db-entry [blockchain query address]
  (log/debug "Checking for transactions made to address " address)
  (dom/letr [transactions (lib/list-transactions blockchain {:received-by-address address})
             _ (when-not transactions (return (f/fail "No transactions found at all")))
             found (filter #(= address (get % "address")) transactions)
             _ (when (empty? found) (return (f/fail "No transactions for the new address found yet")))
             transaction-ids (first (mapv #(get % "txids") found))
             _ (log/debug "Transaction was made to " address " with ids " transaction-ids)
             blockchain-transactions (mapv #(lib/get-transaction blockchain %) transaction-ids)
             _ (when (empty? blockchain-transactions) (f/fail (str "Somehow could not retrieve transactions for the ids " transaction-ids)))]
            ;; When a transaction is made write to DB and interrupt the loop
            (mapv
             (fn [transaction]
               (let [transaction-id (get transaction "txid")]
                 (f/when-let-failed? [fail (lib/get-transaction (get-db-blockchain blockchains) transaction-id)]
                   (lib/create-transaction (get-db-blockchain blockchains)
                                           ;; from
                                           (get (first (filter
                                                        #(= "send" (get % "category"))
                                                        (get transaction "details")))
                                                "address")
                                           ;; amount
                                           (-> transaction
                                               (get "details")
                                               (as-> details (filter #(= "receive" (get % "category")) details))
                                               first
                                               (log/spy (get "amount")))
                                           ;; to
                                           (or (:to-id query) address)
                                           (-> query 
                                               (dissoc :comment :commentto)
                                               (assoc :transaction-id transaction-id
                                                      :currency (:blockchain query)))))))
             blockchain-transactions)))

(def rest-api
  (api
    {:swagger
     {:ui "/"
      :spec "/swagger.json"
      :data {:info
             {:version (clojure.string/trim (slurp "VERSION"))
              :title "Social-wallet-api"
              :description "Social Wallet REST API backend for webapps. All blockchain activity is backed by a DB. For example for any transaction or move that happens on the blockchain side a record will be created on the DB side and the fees will be updated where applicable."
              :contact {:url "https://github.com/Commonfare-net/social-wallet-api"}}}}}

    (context (path-with-version "") []
             :tags ["INFO"]
             (GET "/readme" request
                  {:headers {"Content-Type"
                             "text/html; charset=utf-8"}
                   :body {:readme (md/md-to-html-string
                                   (slurp "README.md"))}}))

    (context (path-with-version "") []
      :tags ["LABEL"]
      (POST "/label" request
        :responses {status/not-found {:schema {:error s/Str}}
                    status/service-unavailable {:schema {:error s/Str}}}
        :return Label
        :body [query Query]
        :summary "Show the blockchain label"
        :description "

Takes a JSON structure made of a `blockchain` identifier.

It returns the label value.

"
                   (with-error-responses blockchains query
                     (fn [blockchain query] {:currency (lib/label blockchain)}))))

    (context (path-with-version "") []
             :tags ["ADDRESS"]
             (POST "/address" request
               :responses {status/not-found {:schema {:error s/Str}}
                           status/service-unavailable {:schema {:error s/Str}}}
                   :return Addresses
                   :body [query PerAccountQuery]
                   :summary "List all addresses related to an account"
                   :description "

Takes a JSON structure made of a `blockchain` identifier and an `account id`.

It returns a list of addresses for the particular account.

"
                   (with-error-responses blockchains query 
                     (fn [blockchain query]
                       (if (= (-> query :blockchain keyword) :mongo)
                         (f/fail "Addresses are available only for blockchain requests")
                         {:addresses (lib/get-address blockchain (:account-id query))})))))

    (context (path-with-version "") []
      :tags ["BALANCE"]
      (POST "/balance" request
        :responses {status/not-found {:schema {:error s/Str}}
                    status/service-unavailable {:schema {:error s/Str}}}
        :return Balance
        :body [query MaybeAccountQuery]
        :summary "Returns the balance of an account or the total balance."
        :description "

Takes a JSON structure made of a `blockchain` identifier and an `account id`.

It returns balance for that particular account. If no account is provided it returns the total balance of the wallet.

"
        (with-error-responses blockchains query
          (fn [blockchain query] {:amount (lib/get-balance blockchain (:account-id query))}))))

    (context (path-with-version "/tags") []
             :tags ["TAGS"]
             (POST "/list" request
               :responses {status/not-found {:schema {:error s/Str}}
                               status/service-unavailable {:schema {:error s/Str}}
                               status/bad-request {:schema {:error s/Str}}}
                   :return Tags
                   :body [query Query]
                   :summary "List all tags"
                   :description "

Takes a JSON structure made of a `blockchain` identifier.

It returns a list of tags found on that blockchain.

"
                   (with-error-responses blockchains query
                     (fn [blockchain query]
                       (if (= (-> query :blockchain keyword) :mongo)
                         {:tags (lib/list-tags blockchain {})}
                         ;; TODO replace mongo eith generic DB or storage?
                         (f/fail "Tags are available only for Mongo requests"))))))

    ;; TODO: maye add the mongo filtering parameters too? Like tags and from/to timestamps
    (context (path-with-version "/transactions") []
             :tags ["TRANSACTIONS"]
             (POST "/list" request
               :responses {status/not-found {:schema {:error s/Str}}
                           status/service-unavailable {:schema {:error s/Str}}}
                   :return  (s/if #(-> % first (get "amount"))
                              [BTCTransaction]
                              [DBTransaction])
                   :body [query ListTransactionsQuery]
                   :summary "List transactions"
                   :description "
Takes a JSON structure with a `blockchain` query identifier. A number of optional identifiers are available for filtering like `account-id`, `count` and `from` for btc like blockains.

Returns a list of transactions found on that blockchain.

"
                   (with-error-responses blockchains query
                     (fn [blockchain query] (lib/list-transactions
                                             blockchain
                                             (cond-> {}
                                               (:account-id query) (assoc :account-id (:account-id query))
                                               (:from query) (assoc :from (:from query))
                                               (:count query) (assoc :count (:count query))))))))

    (context (path-with-version "/transactions") []
             :tags ["TRANSACTIONS"]
             (POST "/get" request
               :responses {status/not-found {:schema {:error s/Str}}
                           status/service-unavailable {:schema {:error s/Str}}}
               :return (s/if #(:transaction-id %)
                         DBTransaction
                         (s/if #(get % "amount")
                           BTCTransaction
                           DecodedRawTransaction))
                   :body [query TransactionQuery]
                   :summary "Retieve a transaction by txid"
                   :description "
Takes a JSON structure with a `blockchain` query identifier and a `txid`.

Returns the transaction if found on that blockchain.

"
                   (with-error-responses blockchains query
                     (fn [blockchain query] (lib/get-transaction
                                             blockchain
                                             (:txid query))))))

    (context (path-with-version "/transactions") []
             :tags ["TRANSACTIONS"]
             (POST "/new" request
               :responses {status/not-found {:schema {:error s/Str}}
                           status/service-unavailable {:schema {:error s/Str}}
                           status/bad-request {:schema {:error s/Str}}}
               :return DBTransaction
               :body [query NewTransactionQuery]
               :summary "Create a new transaction"
               :description "
Takes a JSON structure with a `blockchain`, `from-account`, `to-account` query identifiers and optionally `tags` as paramaters. Tags are metadata meant to add a category to the transaction and useful for grouping and searching.

Creates a transaction. This call is only meant for DBs and not for blockchains.

Returns the DB entry that was created.

"
               (with-error-responses blockchains query
                 (fn [blockchain query]
                   (if (= (-> query :blockchain keyword) :mongo)
                     (f/if-let-ok? [parsed-amount (lib-utils/validate-input-amount (:amount query))]
                       (lib/create-transaction blockchain
                                               (:from-id query)
                                               parsed-amount
                                               (:to-id query)
                                               query)
                       (f/fail (f/message parsed-amount)))
                     (f/fail "Transactions can only be made for DBs. For BLockchain please look at Deposit and Withdraw"))))))

    (context (path-with-version "/withdraws") []
             :tags ["WITHDRAWS"]
             (POST "/new" request
               :responses {status/not-found {:schema {:error s/Str}}
                           status/service-unavailable {:schema {:error s/Str}}
                           status/bad-request {:schema {:error s/Str}}}
               :return DBTransaction
               :body [query NewWithdraw]
               :summary "Perform a withrdaw from a blockchain"
               :description "
Takes a JSON structure with a `blockchain`, `to-address`, `amount` query identifiers and optionally `from-id`, `from-wallet-account`, `tags`, `comment` and `commentto` as paramaters. Comment and commentto are particular to the BTC RCP, for more details look at https://en.bitcoin.it/wiki/Original_Bitcoin_client/API_calls_list. Tags are metadata meant to add a `label` to the withdraw and useful for grouping and searching. The parameter `from-id` is metadata not used in the actual blockchain transaction but stored on the db and useful to identify which account initiated the withdraw. Finally `from-wallet-account` if used will make the withdraw from the particular account in the wallet instead of the default. If not found an error will be returned.

This calll will withdraw an amount from the default account \"\" or optionally a given wallet-account to a provided blockchain address. Also a transaction on the DB will be registered. If fees apply for this transaction those fees will be added to the amount on the DB when the transaction reaches the required amount of confirmations. The number of confirmations and the frequency of the checks are defined in the config as `number-confirmations` and `frequency-confirmations-millis` respectiviely.


Returns the DB entry that was created.

"
                   (with-error-responses blockchains query
                     (fn [blockchain query] 
                       (if (= (-> query :blockchain keyword) :mongo)
                         (f/fail "Withdraws are only available for blockchain requests")
                         (f/if-let-ok? [transaction-id (lib/create-transaction
                                                        blockchain
                                                        (or (:from-wallet-account query) "")
                                                        (:amount query)
                                                        (:to-address query)
                                                        (dissoc query :tags))]
                           (do
                             ;; Update fee to db when confirmed
                             ;; The logged-future will return an exception which otherwise would be swallowed till deref
                             (log/logged-future
                              (while (> (-> blockchain :confirmations :number-confirmations)
                                        (number-confirmations blockchain transaction-id))
                                 (log/debug "Not enough confirmations for transaction with id " transaction-id)
                                 (Thread/sleep (-> blockchain :confirmations :frequency-confirmations-millis)))
                               (let [fee (freecoin-lib.utils/bigdecimal->long
                                          (get
                                           (lib/get-transaction blockchain transaction-id)
                                           "fee"))]
                                 (log/debug "Updating the amount with the fee")
                                 (lib/update-transaction
                                  (get-db-blockchain blockchains) transaction-id
                                  ;; Here we add the minus fee to the whole transaction when confirmed
                                  (fn [tr] (update tr :amount #(+ % (- fee)))))))
                             ;; store to db as well with transaction-id
                             (lib/create-transaction (get-db-blockchain blockchains)
                                                     (or (:from-id query) (:from-wallet-account query) "")
                                                     (:amount query)
                                                     (:to-address query)
                                                     (-> query
                                                         (dissoc :comment :comment-to)
                                                         (assoc :transaction-id transaction-id
                                                                :currency (:blockchain query)))))
                           ;; There was an error
                           (f/fail (f/message transaction-id))))))))

        (context (path-with-version "/deposits") []
             :tags ["DEPOSITS"]
             (POST "/new" request
               :responses {status/not-found {:schema {:error s/Str}}
                           status/service-unavailable {:schema {:error s/Str}}
                           status/bad-request {:schema {:error s/Str}}}
               :return AddressNew
               :body [query NewDeposit]

               :summary "Request a new blockchain address to perform a deposit"
               :description "
Takes a JSON structure with a `blockchain` query identifier and optionally `to-id`, `to-wallet-id` and `tags`. `to-id` is metadata that will be added to the DB once a deposit to the address is detected. Same goes for `tags` which are metadata meant to add a `label` to the deposit and they are useful for grouping and searching. When `to-wallet-id` is used it will create the address for a particular account in the wallet and the default otherwise. If the account is not found the address will be created on the default account.

This call creates a new address and returns it in order to be able to deposit to it. Then, on a different thread, there will be a watch that until it expires it will check for a transaction done to this address and update the DB. If no transaction is perfromed until expiration a check for that particular address can be triggered via `deposits/check`. The frequency of the transaction checks and the expiration can be set in the config as `frequency-deposit-millis` and `deposit-expiration-millis` respectively.

Returns the blockchain address that was created.

"
                   (with-error-responses blockchains query
                     (fn [blockchain query]
                       (if (= (-> query :blockchain keyword) :mongo)
                         (f/fail "Deposits are only available for blockchain requests")
                         (f/if-let-ok? [new-address (lib/create-address blockchain
                                                                        (-> query :to-wallet-id))]
                           (let [pending (atom true)
                                 start-time (time/now)
                                 end-time (time/add-milliseconds start-time (-> blockchain :deposits :deposit-expiration-millis))]
                             ;; Check whether a transaction to this address was made and update the DB
                             ;; The logged-future will return an exception which otherwise would be swallowed till deref
                             (log/logged-future
                              (while (and @pending (time/< (time/now) end-time))
                                (when-not (empty? (blockchain-deposit->db-entry blockchain query new-address))
                                  (reset! pending false))
                                ;; wait
                                (Thread/sleep (-> blockchain :confirmations :frequency-confirmations-millis))))
                             {:address new-address})
                           ;; There was an error
                           (f/fail (f/message new-address))))))))

        (context (path-with-version "/deposits") []
          :tags ["DEPOSITS"]
          (POST "/check" request
            :responses {status/not-found {:schema {:error s/Str}}
                        status/service-unavailable {:schema {:error s/Str}}
                        status/bad-request {:schema {:error s/Str}}}
            :return (s/if #(coll? %)
                        (s/if #(first %)
                          [DBTransaction]
                          [])
                        {:error s/Str}) 
            :body [query DepositCheck]

            :summary "Manually check if a given address has received any deposits"
            :description "
Takes a JSON structure with a `blockchain` and an `address` query identifier.

This call will check if any deposits were made to this particular address and will update the DB if it is not already updated. It is meant to be used only for blockchains and the purpose is to update the db for deposits that were made after the deposit watch for the address has expired before a deposit was made. If it is called even though the deposits have been registerd no changes will be made.

Returns the DB entries that were created.

"
            (with-error-responses blockchains query
              (fn [blockchain query]
                (if (= (-> query :blockchain keyword) :mongo)
                  (f/fail "Deposit checks are only available for blockchain requests")
                  (blockchain-deposit->db-entry blockchain query (:address query)))))))


        
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
