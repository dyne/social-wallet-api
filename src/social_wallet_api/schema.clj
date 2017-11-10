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

(ns social-wallet-api.schema
  (:require [schema.core :as s]
            [ring.swagger.json-schema :as rjs]))

;; auxiliary wrapper for comfort declaring defaults in schemas
(defn- k [type key default]
  (rjs/field type {:example (get default key)}))

(s/defschema Query
  "POST Wallet query validator"
  {:blockchain (rjs/field s/Str {:example "mongo"})})

(s/defschema PerAccountQuery
  "POST Wallet query validator for requests per account"
  (merge Query {:account-id (rjs/field s/Str {:example "account-id"})}))

(s/defschema Account
  "Account schema validator"
  ;; first-name last-name email password flags
  {:first-name s/Str
   (s/optional-key :last-name)  [s/Str]
   (s/required-key :email)      [s/Str]
   (s/required-key :password)   [s/Str]
   (s/required-key :flags)      [s/Str]})

(s/defschema Tag
  "Tag schema validator"
  ;; tag(string) created-by(string) created(timestamp)
  {(s/required-key :tag)        s/Str
   (s/required-key :count)      s/Num
   (s/required-key :amount)     s/Num
   (s/required-key :created-by) s/Any
   (s/required-key :created)    s/Any})


(s/defschema DBTransaction
  "Transaction schema validator"
  {(s/optional-key :tags)      [s/Str]
   (s/optional-key :timestamp)  s/Str
   (s/optional-key :from-id)    s/Any
   (s/optional-key :to-id)      s/Any
   (s/optional-key :amount)     s/Num
   (s/optional-key :transaction-id) (s/maybe s/Str)
   (s/optional-key :currency) s/Str
   ;; how many confirmations needed for a transaction to be confirmed
   (s/optional-key :number-confirmations) s/Num})

(s/defschema AccountDetails
  {(s/required-key "account") s/Str
   (s/required-key "address") s/Str
   (s/required-key "category") s/Str
   (s/required-key "amount") s/Num
   (s/required-key "label") s/Str
   (s/required-key "vout") s/Num
   (s/optional-key "fee") s/Num
   (s/optional-key "abandoned") s/Bool})

(s/defschema BTCTransaction  
  {(s/required-key "amount") s/Num
   (s/optional-key "category") s/Str
   (s/optional-key "trusted") s/Bool
   (s/optional-key "label") s/Str
   (s/optional-key "vout") s/Num
   (s/optional-key "fee") s/Num
   (s/optional-key "confirmations") s/Num
   (s/optional-key "blockhash") s/Str
   (s/optional-key "blockindex") s/Num
   (s/optional-key "blocktime") s/Num
   (s/optional-key "walletconflicts") [s/Any]
   (s/optional-key "time") s/Num
   (s/optional-key "otheraccount") s/Str
   (s/optional-key "timereceived") s/Num
   (s/optional-key "bip125-replaceable") s/Str
   (s/optional-key "abandoned") s/Bool
   (s/optional-key "comment") s/Str
   (s/optional-key "hex") s/Str
   (s/optional-key "txid") s/Str
   (s/optional-key "address") s/Str
   (s/optional-key "account") s/Str
   (s/optional-key "details") [AccountDetails] ;; TODO
   })

(s/defschema TransactionQuery
  (merge Query {:txid s/Str}))

(s/defschema NewTransactionQuery
  (assoc Query
         (s/optional-key :from-id)    s/Any
         (s/optional-key :to-id)      s/Any
         (s/optional-key :amount)     s/Num
         (s/optional-key :tags)      [s/Str]))

(s/defschema ListTransactionsQuery
  (merge Query {(s/optional-key :account-id) s/Str
                ;; TODO this way they would only work for Mongo. We need some sort of paging
                #_(s/optional-key :count) s/Num
                #_(s/optional-key :from) s/Num}))

;; Blockchain Address
(def Address s/Str)

(def Balance s/Num)
