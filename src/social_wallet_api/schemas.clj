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

(ns social-wallet-api.schemas
  (:require [schema.core :as s]
            [ring.swagger.json-schema :as rjs]))

;; auxiliary wrapper for comfort declaring defaults in schemas
(defn- k [type key default]
  (rjs/field type {:example (get default key)}))

(s/defschema Accounts
  "Account schema validator"
  ;; first-name last-name email password flags
  {:first-name s/Str
   (s/optional-key :last-name)  [s/Str]
   (s/required-key :email)      [s/Str]
   (s/required-key :password)   [s/Str]
   (s/required-key :flags)      [s/Str]})

(s/defschema Tags
  "Tag schema validator"
  ;; tag(string) created-by(string) created(timestamp)
  {(s/required-key :tag) [s/Str]
   (s/required-key :created-by) [s/Str]
   (s/required-key :created) [s/Str]})

(s/defschema Transactions
  "Transaction schema validator"
  ;; {:_id (str timestamp "-" from-account-id)
  ;;  :currency "MONGO"
  ;;  :timestamp timestamp
  ;;  :from-id from-account-id
  ;;  :to-id to-account-id
  ;;  :tags tags
  ;;  :amount (util/bigdecimal->long amount)}]
  {(s/required-key :currency) [s/Str]
   (s/required-key :timestamp) [s/Str]
   (s/required-key :from-id) [s/Str]
   (s/required-key :to-id) [s/Str]
   (s/required-key :amount) [s/Num]})
