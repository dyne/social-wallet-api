;; Social Wallet REST API

;; Copyright (C) 2017- Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Denis Roio <jaromil@dyne.org>
;; Aspasia Beneti <aspra@dyne.org>

;; This file is part of Social Wallet REST API.

;; Social Wallet REST API is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

;; Social Wallet REST API is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.

;; Additional permission under GNU AGPL version 3 section 7.

;; If you modify Social Wallet REST API, or any covered work, by linking or combining it with any library (or a modified version of that library), containing parts covered by the terms of EPL v 1.0, the licensors of this Program grant you additional permission to convey the resulting work. Your modified version must prominently offer all users interacting with it remotely through a computer network (if your version supports such interaction) an opportunity to receive the Corresponding Source of your version by providing access to the Corresponding Source from a network server at no charge, through some standard or customary means of facilitating copying of software. Corresponding Source for a non-source form of such a combination shall include the source code for the parts of the libraries (dependencies) covered by the terms of EPL v 1.0 used as well as that of the covered work.

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
            [dom-top.core :as dom]
            [ring.middleware.cors :refer [wrap-cors]]
            [clj-time.core :as time]
            [social-wallet-api.api-key :refer [create-and-store-apikey! fetch-apikey apikey
                                               write-apikey-file]]))

(defonce prod-app-name "social-wallet-api")
(defonce config-default (config-read prod-app-name))

(defonce connections (atom {}))
(defonce client (atom nil))

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

;; TODO: pass conncetion as arg?
(defn- get-connection [connections query]
  (get @connections (-> query :connection keyword)))

(defn- get-db-connection [connections]
  (:mongo @connections))

(defn- get-connection-conf [config app-name connection]
  (get-in config [(keyword app-name) :freecoin connection]))

(defn- get-app-conf [config app-name]
  (get-in config [(keyword app-name) :freecoin]))

(defn- number-confirmations [connection transaction-id]
  (-> (lib/get-transaction connection transaction-id)
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
   (let [mongo-conf (get-app-conf config app-name) 
         mongo (lib/new-mongo (->  mongo-conf :mongo :currency)
                              (freecoin/connect-mongo (dissoc mongo-conf :currency)))]
     (swap! connections conj {:mongo mongo})
     (log/warn "MongoDB backend connected.")

     ;; Setup API KEY when needed
     (let [apikey-store (-> @connections :mongo :stores-m :apikey-store)]
       (when-let [client-app (:apikey mongo-conf)]
         (reset! client client-app)
         (reset! apikey (apply hash-map (vals
                                         (or (fetch-apikey apikey-store client-app)
                                             (create-and-store-apikey! apikey-store client-app 32)))))
         (write-apikey-file "apikey.yaml" (str client-app ":\n " (get @apikey client-app))))))
   
   (when-let [fair-conf (get-connection-conf config app-name :faircoin)]
     (f/if-let-ok? [fair (merge (lib/new-btc-rpc (:currency fair-conf) 
                                                 (:rpc-config-path fair-conf))
                                {:confirmations {:number-confirmations (:number-confirmations fair-conf)
                                                 :frequency-confirmations-millis (:frequency-confirmations-millis fair-conf)}}
                                {:deposits {:deposit-expiration-millis (:deposit-expiration-millis fair-conf)
                                            :frequency-deposit-millis (:frequency-deposit-millis fair-conf)}})]
       ;; TODO add schema fair
       (do
         (swap! connections conj {:faircoin fair})
         (log/info "Faircoin config is loaded"))
       (log/error (f/message fair))))))

(defn destroy []
  (log/warn "Stopping the Social Wallet API.")
  (freecoin/disconnect-mongo (:mongo @connections))
  (reset! client "")
  (reset! apikey {}))

(defn- with-error-responses [connections query ok-fn]
  (try
    (if-let [connection (get-connection connections query)]
      (if (and (not (instance? freecoin_lib.core.Mongo connection)) (= "db-only" (:type query)))
        (not-found {:error "The connection is not of type db-only."})
        (f/if-let-ok? [response (ok-fn connection query)]
          (ok response)
          (bad-request {:error (f/message response)})))
      (not-found {:error "No such connection can be established."}))
    (catch java.net.ConnectException e
      (service-unavailable {:error "There was a connection issue."}))))


(defn- blockchain-deposit->db-entry [connection query address]
  (log/debug "Checking for transactions made to address " address)
  (dom/letr [transactions (lib/list-transactions connection {:received-by-address address})
             _ (when-not transactions (return (f/fail "No transactions found at all")))
             found (filter #(= address (get % "address")) transactions)
             _ (when (empty? found) (return (f/fail "No transactions for the new address found yet")))
             transaction-ids (first (mapv #(get % "txids") found))
             _ (log/debug "Transaction was made to " address " with ids " transaction-ids)
             blockchain-transactions (mapv #(lib/get-transaction connection %) transaction-ids)
             _ (when (empty? blockchain-transactions) (f/fail (str "Somehow could not retrieve transactions for the ids " transaction-ids)))]
            ;; When a transaction is made write to DB and interrupt the loop
            (mapv
             (fn [transaction]
               (let [transaction-id (get transaction "txid")]
                 (f/when-let-failed? [fail (lib/get-transaction (get-db-connection connections) transaction-id)]
                   (lib/create-transaction (get-db-connection connections)
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
                                               (get "amount"))
                                           ;; to
                                           (or (:to-id query) address)
                                           (-> query 
                                               (dissoc :comment :commentto)
                                               (assoc :transaction-id transaction-id
                                                      :currency (:connection query)))))))
             blockchain-transactions)))

(defn wrap-auth [handler]
  (fn [request]
    (if @client
      (if (= (-> request :headers (get "x-api-key"))
             (get @apikey @client))
        (handler request)
        (unauthorized {:error "Could not access the Social Wallet API: wrong API KEY"}))
      (handler request))))

(def rest-api
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:securityDefinitions
            {:api_key 
             {:type "apiKey"
              :name "x-api-key"
              :in "header"}}
            :info
            {:version (clojure.string/trim (slurp "VERSION"))
             :title "Social-wallet-api"
             :description "Social Wallet REST API backend for webapps. All blockchain activity is backed by a DB. For example for any transaction that happens on the blockchain side a record will be created on the DB side and the fees will be updated where applicable. All queries take as minimum parameters the `type` which can be one the [\"db-only\" \"blockchain-and-db\"] and the `connection` which can be \"mongo\" for db-only or \"faircoin\", \"bitcoin\" etc for blockchain-and-db."
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
     :middleware [wrap-auth]
     (POST "/label" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}}
       :return Label
       :body [query Query]
       :summary "Show the label"
       :description "

Takes a JSON structure made of a `connection` and a `type` identifier.

It returns the label value which contains the name of the currency.

"
       (with-error-responses connections query
         (fn [connection query] {:currency (lib/label connection)}))))

   (context (path-with-version "") []
     :tags ["ADDRESS"]
     :middleware [wrap-auth]
     (POST "/address" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}}
       :return Addresses
       :body [query PerAccountQuery]
       :summary "List all addresses related to an account"
       :description "

Takes a JSON structure made of a `connection` identifier, a `type` and an `account-id`.

It returns a list of addresses for the particular account.

"
       (with-error-responses connections query 
         (fn [connection query]
           (if (= (-> query :connection keyword) :mongo)
             (f/fail "Addresses are available only for blockchain requests")
             {:addresses (lib/get-address connection (:account-id query))})))))

   (context (path-with-version "") []
     :tags ["BALANCE"]
     :middleware [wrap-auth]
     (POST "/balance" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}}
       :return Balance
       :body [query MaybeAccountQuery]
       :summary "Returns the balance of an account or the total balance."
       :description "

Takes a JSON structure made of a `connection`, `type` identifier and an `account id`.

It returns balance for that particular account. If no account is provided it returns the total balance of the wallet.

"
       (with-error-responses connections query
         (fn [connection query] {:amount (lib/get-balance connection (:account-id query))}))))

   (context (path-with-version "/tags") []
     :tags ["TAGS"]
     :middleware [wrap-auth]
     (POST "/list" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}
                   status/bad-request {:schema {:error s/Str}}}
       :return Tags
       :body [query Query]
       :summary "List all tags"
       :description "

Takes a JSON structure made of a `connection` and a `type` identifier.

It returns a list of tags found on the database.

"
       (with-error-responses connections query
         (fn [connection query]
           (if (= (-> query :connection keyword) :mongo)
             {:tags (lib/list-tags connection {})}
             ;; TODO replace mongo eith generic DB or storage?
             (f/fail "Tags are available only for Mongo requests"))))))

   ;; TODO: maye add the mongo filtering parameters too? Like tags and from/to timestamps
   (context (path-with-version "/transactions") []
     :tags ["TRANSACTIONS"]
     :middleware [wrap-auth]
     (POST "/list" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}}
       :return  (s/if #(map? %)
                  {:total-count s/Num
                   :transactions [DBTransaction]}
                  [BTCTransaction])
       :body [query ListTransactionsQuery]
       :summary "List transactions"
       :description "
Takes a JSON structure with a `connection` and a `type` query identifier. Both mongo and btc transactions can be filtered by `account-id`. For blockchains, a number of optional identifiers are available for filtering like `count` and `from`: Returns up to [count] most recent transactions skipping the first [from] transactions for account [account]. For db queries paging can be used with the `page` and `per-page` identifiers which default to 1 and 10 respectively (first page, ten per page). Finally db queries can be also filtered by `currency`, `tags`, `description`, `from-datetime` and `to-datetime`. From-datetime is inclusive and to-datetime is exclusive. 

Returns a list of transactions found on that connection.

"
       (with-error-responses connections query
         (fn [connection {:keys [account-id tags from-datetime to-datetime page per-page currency description]}]
           (f/if-let-ok? [transaction-list (lib/list-transactions
                                            connection
                                            (cond-> {}
                                              account-id  (assoc :account-id account-id)
                                              from-datetime  (assoc :from from-datetime)
                                              to-datetime  (assoc :to to-datetime)
                                              tags (assoc :tags tags)
                                              page (assoc :page page)
                                              per-page (assoc :per-page per-page)
                                              ;; TODO: currency filtering doesnt work yet
                                              currency (assoc :currency currency)
                                              description (assoc :description description)))]
             (if (= (-> query :connection keyword) :mongo)
               {:total-count (lib/count-transactions connection {})
                :transactions transaction-list}
               transaction-list)
             transaction-list)))))

   (context (path-with-version "/transactions") []
     :tags ["TRANSACTIONS"]
     :middleware [wrap-auth]
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
Takes a JSON structure with a `connection`, `type` query identifier and a `txid`.

Returns the transaction if found on that connection.

"
       (with-error-responses connections query
         (fn [connection query] (lib/get-transaction
                                 connection
                                 (:txid query))))))

   (context (path-with-version "/transactions") []
     :tags ["TRANSACTIONS"]
     :middleware [wrap-auth]
     (POST "/new" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}
                   status/bad-request {:schema {:error s/Str}}}
       :return DBTransaction
       :body [query NewTransactionQuery]
       :summary "Create a new transaction"
       :description "
Takes a JSON structure with a `connection`, `type`, `from-account`, `to-account`, `amount` query identifiers and optionally `tags` and `description` as paramaters. Tags are metadata meant to add a category to the transaction and useful for grouping and searching. The amount has been tested for values between `0.00000001` and `9999999999999999.99999999`.

Creates a transaction. This call is only meant for DBs and not for blockchains.

Returns the DB entry that was created.

"
       (with-error-responses connections query
         (fn [connection query]
           (if (= (-> query :connection keyword) :mongo)
             (lib/create-transaction connection
                                     (:from-id query)
                                     (:amount query)
                                     (:to-id query)
                                     query) 
             (f/fail "Transactions can only be made for DBs. For blockchains please look at Deposit and Withdraw"))))))

   (context (path-with-version "/withdraws") []
     :tags ["WITHDRAWS"]
     :middleware [wrap-auth]
     (POST "/new" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}
                   status/bad-request {:schema {:error s/Str}}}
       :return DBTransaction
       :body [query NewWithdraw]
       :summary "Perform a withrdaw from a blockchain"
       :description "
Takes a JSON structure with a `connection`, `type`, `to-address`, `amount` query identifiers and optionally `from-id`, `from-wallet-account`, `tags`, `comment`, `commentto` and `description` as paramaters. Comment and commentto are particular to the BTC RCP, for more details look at https://en.bitcoin.it/wiki/Original_Bitcoin_client/API_calls_list. Tags are metadata meant to add a `label` to the withdraw and useful for grouping and searching. The parameter `from-id` is metadata not used in the actual blockchain transaction but stored on the db and useful to identify which account initiated the withdraw. Finally `from-wallet-account` if used will make the withdraw from the particular account in the wallet instead of the default. If not found an error will be returned.

This call will withdraw an amount from the default account \"\" or optionally a given wallet-account to a provided blockchain address. Also a transaction on the DB will be registered. If fees apply for this transaction those fees will be added to the amount on the DB when the transaction reaches the required amount of confirmations. The number of confirmations and the frequency of the checks are defined in the config as `number-confirmations` and `frequency-confirmations-millis` respectiviely.


Returns the DB entry that was created.

"
       (with-error-responses connections query
         (fn [connection query] 
           (if (= (-> query :connection keyword) :mongo)
             (f/fail "Withdraws are only available for blockchain requests")
             (f/if-let-ok? [transaction-id (lib/create-transaction
                                            connection
                                            (or (:from-wallet-account query) "")
                                            (-> query :amount)
                                            (:to-address query)
                                            (dissoc query :tags :description))]
               (do
                 ;; Update fee to db when confirmed
                 ;; The logged-future will return an exception which otherwise would be swallowed till deref
                 (log/logged-future
                  (while (> (-> connection :confirmations :number-confirmations)
                            (number-confirmations connection transaction-id))
                    (log/debug "Not enough confirmations for transaction with id " transaction-id)
                    (Thread/sleep (-> connection :confirmations :frequency-confirmations-millis)))
                  (let [transaction (lib/get-transaction connection transaction-id)
                        fee (get transaction "fee")]
                    (log/debug "Updating the amount with the fee")
                    (lib/update-transaction
                     (get-db-connection connections) transaction-id
                     ;; Here we add the minus fee to the whole transaction when confirmed
                     (fn [tr] (let [updated-transaction (update tr :amount #(+ % (- fee)))]
                                (assoc updated-transaction :amount-text (-> updated-transaction :amount str)))))))
                 ;; store to db as well with transaction-id
                 (f/if-let-ok? [db-transaction
                                (lib/create-transaction (get-db-connection connections)
                                                        (or (:from-id query) (:from-wallet-account query) "")
                                                        (:amount query)
                                                        (:to-address query)
                                                        (-> query
                                                            (dissoc :comment :comment-to)
                                                            (assoc :transaction-id transaction-id
                                                                   :currency (:connection query))))]
                   db-transaction
                   (f/fail (f/message (str "Did not write transaction " transaction-id " on the DB because: " (f/message db-transaction))))))
               ;; There was an error
               (f/fail (f/message transaction-id))))))))

   (context (path-with-version "/deposits") []
     :tags ["DEPOSITS"]
     :middleware [wrap-auth]
     (POST "/new" request
       :responses {status/not-found {:schema {:error s/Str}}
                   status/service-unavailable {:schema {:error s/Str}}
                   status/bad-request {:schema {:error s/Str}}}
       :return AddressNew
       :body [query NewDeposit]

       :summary "Request a new blockchain address to perform a deposit"
       :description "
Takes a JSON structure with a `connection` and `type` query identifier and optionally `to-id`, `to-wallet-id` and `tags`. `to-id` is metadata that will be added to the DB once a deposit to the address is detected. Same goes for `tags` which are metadata meant to add a `label` to the deposit and they are useful for grouping and searching. When `to-wallet-id` is used it will create the address for a particular account in the wallet and the default otherwise. If the account is not found the address will be created on the default account.

This call creates a new address and returns it in order to be able to deposit to it. Then, on a different thread, there will be a watch that until it expires it will check for a transaction done to this address and update the DB. If no transaction is perfromed until expiration a check for that particular address can be triggered via `deposits/check`. The frequency of the transaction checks and the expiration can be set in the config as `frequency-deposit-millis` and `deposit-expiration-millis` respectively.

Returns the blockchain address that was created.

"
       (with-error-responses connections query
         (fn [connection query]
           (if (= (-> query :connection keyword) :mongo)
             (f/fail "Deposits are only available for blockchain requests")
             (f/if-let-ok? [new-address (lib/create-address connection
                                                            (-> query :to-wallet-id))]
               (let [pending (atom true)
                     start-time (time/now)
                     end-time (time/plus start-time (time/millis (-> connection :deposits :deposit-expiration-millis)))]
                 ;; Check whether a transaction to this address was made and update the DB
                 ;; The logged-future will return an exception which otherwise would be swallowed till deref
                 (log/logged-future
                  (while (and @pending (time/before? (time/now) end-time))
                    (when-not (empty? (blockchain-deposit->db-entry connection query new-address))
                      (reset! pending false))
                    ;; wait
                    (Thread/sleep (-> connection :confirmations :frequency-confirmations-millis))))
                 {:address new-address})
               ;; There was an error
               (f/fail (f/message new-address))))))))

   (context (path-with-version "/deposits") []
     :tags ["DEPOSITS"]
     :middleware [wrap-auth]
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
Takes a JSON structure with a `connection`, `type` and an `address` query identifier.

This call will check if any deposits were made to this particular address and will update the DB if it is not already updated. It is meant to be used only for blockchains and the purpose is to update the db for deposits that were made after the deposit watch for the address has expired before a deposit was made. If it is called even though the deposits have been registerd no changes will be made.

Returns the DB entries that were created.

"
       (with-error-responses connections query
         (fn [connection query]
           (if (= (-> query :connection keyword) :mongo)
             (f/fail "Deposit checks are only available for blockchain requests")
             (blockchain-deposit->db-entry connection query (:address query)))))))


   
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
  (wrap-cors
   (wrap-defaults rest-api rest-api-defaults)
   :access-control-allow-origin [#".*"]
   :access-control-allow-methods [:get :post]))
