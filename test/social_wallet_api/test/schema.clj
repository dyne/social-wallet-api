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
  (:require [midje.sweet :refer [fact truthy =>]]
            [social-wallet-api.schema :refer [AccountDetails BTCTransaction
                                              DecodedRawTransaction SawtoothTransaction
                                              SawtoothTransactions]]
            [schema.core :as s]
            [cheshire.core :as json]))

(fact "Check some schemas"
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/sample-btc-transaction-response.json")))  => truthy
      
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/confirmed-transaction-faircoin.json")))  => truthy

      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/not-confirmed-transaction-faircoin.json")))  => truthy

      (s/validate DecodedRawTransaction (json/parse-string (slurp "test-resources/sample-decoded-raw-transaction.json"))) => truthy

      (s/validate SawtoothTransactions  {"data" [{"header" {"signer_public_key" "033666c2ac08d8742b0cb42b26924cdb4b1e3a02998128e8496013e808604f6d60", "dependencies" [], "batcher_public_key" "033666c2ac08d8742b0cb42b26924cdb4b1e3a02998128e8496013e808604f6d60", "outputs" ["000000a87cb5eafdcca6a8cde0fb0dec1400c5ab274474a6aa82c1c0cbf0fbcaf64c0b" "000000a87cb5eafdcca6a8cde0fb0dec1400c5ab274474a6aa82c12840f169a04216b7"], "family_version" "1.0", "inputs" ["000000a87cb5eafdcca6a8cde0fb0dec1400c5ab274474a6aa82c1c0cbf0fbcaf64c0b" "000000a87cb5eafdcca6a8cde0fb0dec1400c5ab274474a6aa82c12840f169a04216b7" "000000a87cb5eafdcca6a8cde0fb0dec1400c5ab274474a6aa82c1918142591ba4e8a7" "000000a87cb5eafdcca6a8cde0fb0dec1400c5ab274474a6aa82c12840f169a04216b7"], "nonce" "", "family_name" "sawtooth_settings", "payload_sha512" "0486cb0ad6a2db602ba447d32e4bdb94f4480714052d9a6839a8d52d1ce4f9c0f1a0a3450cc590b63e73f63bc27b66c5df821a3f000d02c2714188b2478c554b"}, "header_signature" "a35e751a46805e0312f9b20637ab672359e7eb65dbd81a1b8d495ac6e03d6e3d691d9d30b33baedefdf26872315ac90279e031c20252d5aaabca1ec47869ff21", "payload" "CAESgAEKJnNhd3Rvb3RoLnNldHRpbmdzLnZvdGUuYXV0aG9yaXplZF9rZXlzEkIwMzM2NjZjMmFjMDhkODc0MmIwY2I0MmIyNjkyNGNkYjRiMWUzYTAyOTk4MTI4ZTg0OTYwMTNlODA4NjA0ZjZkNjAaEjB4ODViMGMxMWQ3OTYxODY1Ng=="}], "head" "6f4742b9d8727b62cc8af907f3b7d0632b9e9d21f0d2aa3a03339c2aae8defdf6077cd687326b012c1e152c3dc54e8d2930f51d5ee33d104fad9af703ec6ba10", "link" "http://localhost:8008/transactions?head=6f4742b9d8727b62cc8af907f3b7d0632b9e9d21f0d2aa3a03339c2aae8defdf6077cd687326b012c1e152c3dc54e8d2930f51d5ee33d104fad9af703ec6ba10&start=a35e751a46805e0312f9b20637ab672359e7eb65dbd81a1b8d495ac6e03d6e3d691d9d30b33baedefdf26872315ac90279e031c20252d5aaabca1ec47869ff21&limit=100", "paging" {"next" "nil" "next_position" "nil" "limit" nil, "start" nil}}) => truthy

      (s/validate SawtoothTransaction (json/parse-string (slurp "test-resources/sample-sawtooth-transaction-response.json"))) => truthy)
