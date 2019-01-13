;; Social Wallet REST API

;; Copyright (C) 2017- Dyne.org foundation

;; Sourcecode designed, written and maintained by
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

(ns social-wallet-api.test.schema
  (:require [midje.sweet :refer :all]
            [social-wallet-api.schema :refer [AccountDetails BTCTransaction
                                              DecodedRawTransaction]]
            [schema.core :as s]
            [cheshire.core :as json]))

(fact "Check some schemas"
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/sample-btc-transaction-response.json")))  => truthy
      
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/confirmed-transaction-faircoin.json")))  => truthy

      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/not-confirmed-transaction-faircoin.json")))  => truthy

      (s/validate DecodedRawTransaction (json/parse-string (slurp "test-resources/sample-decoded-raw-transaction.json"))) => truthy)
