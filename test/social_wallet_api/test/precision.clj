;; Social Wallet REST API

;; Copyright (C) 2018- Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Aspasia Beneti <aspra@dyne.org>

;; This file is part of Social Wallet REST API.

;; Social Wallet REST API is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

;; Social Wallet REST API is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.

;; Additional permission under GNU AGPL version 3 section 7.

;; If you modify Social Wallet REST API, or any covered work, by linking or combining it with any library (or a modified version of that library), containing parts covered by the terms of EPL v 1.0, the licensors of this Program grant you additional permission to convey the resulting work. Your modified version must prominently offer all users interacting with it remotely through a computer network (if your version supports such interaction) an opportunity to receive the Corresponding Source of your version by providing access to the Corresponding Source from a network server at no charge, through some standard or customary means of facilitating copying of software. Corresponding Source for a non-source form of such a combination shall include the source code for the parts of the libraries (dependencies) covered by the terms of EPL v 1.0 used as well as that of the covered work.

(ns social-wallet-api.test.precision
  (:require [midje.sweet :refer [=> against-background before after facts fact]]
            [ring.mock.request :as mock]
            [social-wallet-api.handler :as h]
            [social-wallet-api.core :as swapi]
            [social-wallet-api.test.handler :refer [test-app-name parse-body mongo-db-only]]
            [auxiliary.config :refer [config-read]]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [clojure.test.check.generators :as gen]
            [midje.experimental :refer [for-all]]
            [freecoin-lib.core :as lib] 
            [clj-storage.core :as store]))

(def Satoshi (BigDecimal. "0.00000001"))
(def int16-fr8 (BigDecimal. "9999999999999999.99999999"))

(def some-from-account "some-from")
(def some-to-account "some-to-account")

(defn- bigint? [x]
  (not= "No bigint"
     (try (and (string? x) (bigint x))
                      (catch NumberFormatException e "No bigint"))))

(defn new-transaction-request [big-number from-account to-account]
  (h/app
   (->
    (mock/request :post "/wallet/v1/transactions/new")
    (mock/content-type "application/json")
    (mock/body  (cheshire/generate-string (merge
                                           mongo-db-only
                                           {:from-id from-account
                                            :to-id to-account
                                            :amount big-number
                                            :tags ["blabla"]}))))))

(defn get-latest-transaction [account-id]
  (first (lib/list-transactions (:mongo @swapi/connections) {:account-id account-id})))

(defn empty-transactions []
  (store/delete-all! (-> @swapi/connections :mongo :stores-m :transaction-store)))

(against-background [(before :contents (swapi/init
                                        (config-read test-app-name)
                                        test-app-name))
                     (after :contents (do
                                        (empty-transactions)
                                        (swapi/destroy)))]
                    (facts "Check specific amounts" 
                           (fact "Check one Satochi (8 decimal)"
                                 (let [response (new-transaction-request (.toString Satoshi)
                                                                         some-from-account
                                                                         some-to-account)
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => Satoshi
                                   (:amount-text body) => (str Satoshi)
                                   (:amount (get-latest-transaction some-from-account)) => Satoshi
                                   (class (:amount (get-latest-transaction some-from-account))) => java.math.BigDecimal
                                   (:amount-text (get-latest-transaction some-from-account)) => (.toString Satoshi)))
                           (fact "16 integer digits and 8 decimal)"
                                 (let [response (new-transaction-request (str int16-fr8)
                                                                         some-from-account
                                                                         some-to-account)
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => int16-fr8
                                   (:amount-text body) => (.toString int16-fr8)
                                   (:amount (get-latest-transaction some-from-account)) => int16-fr8
                                   (class (:amount (get-latest-transaction some-from-account))) => java.math.BigDecimal                    
                                   (:amount-text (get-latest-transaction some-from-account)) => (.toString int16-fr8)))
                           (fact "Some other amount 23423454565.45645645"
                                 (let [big-number (BigDecimal. "23423454565.45645645")
                                       response (new-transaction-request (.toString big-number)
                                                                         some-from-account
                                                                         some-to-account)
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => big-number
                                   (:amount-text body) => (.toString big-number)
                                   (:amount (get-latest-transaction some-from-account)) => big-number
                                   (class (:amount (get-latest-transaction some-from-account))) => java.math.BigDecimal                    
                                   (:amount-text (get-latest-transaction some-from-account)) => (.toString big-number)))
                           
                           (fact "Negative amounts not allowed"
                                 (let [some-negative -16.5
                                       response (new-transaction-request (str some-negative)
                                                                         some-from-account
                                                                         some-to-account)
                                       body (parse-body (:body response))]
                                   (:status response) => 400
                                   (:error body) => "Negative values not allowed."
                                   (:amount-text body) => nil))

                           (fact "Check amount zero"
                                 (let [response (new-transaction-request "0"
                                                                         some-from-account
                                                                         some-to-account)
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount-text body) => "0")))
                    
                    (facts "Check different doubles" :slow
                           (let [sum-to-account (atom (BigDecimal. 0))]
                             (for-all
                              [rand-double (gen/double* {:min Satoshi
                                                         :max int16-fr8
                                                         :NaN? false
                                                         :infinite? false})]
                              {:num-tests 200}
                              (fact "Generative tests"
                                    (let [amount (.toString rand-double)  
                                          response (new-transaction-request amount
                                                                            "other-from-account"
                                                                            "other-to-account")
                                          body (parse-body (:body response))
                                          _ (swap! sum-to-account #(.add % (BigDecimal. amount)))]
                                      (:status response) => 200
                                      ;; To create a BigDecimal parse from string otherwise
                                      ;; (BigDecimal.  0.5000076293945312) => 0.50000762939453125M
                                      ;; whereas:  "0.5000076293945312") =>   0.5000076293945312M
                                      (BigDecimal. (str (:amount body))) => (BigDecimal. amount)
                                      (-> (get-latest-transaction "other-from-account")
                                                   :amount
                                                   .toString
                                                   BigDecimal.) => (BigDecimal. amount) 
                                      (:amount-text (get-latest-transaction "other-from-account")) => amount)))
                             (fact "Balance works properly"
                                   (let [response (h/app
                                                   (->
                                                    (mock/request :post "/wallet/v1/balance")
                                                    (mock/content-type "application/json")
                                                    (mock/body  (cheshire/generate-string (assoc
                                                                                           mongo-db-only
                                                                                           :account-id "other-to-account")))))
                                         body (parse-body (:body response))]
                                     (:amount body) => @sum-to-account)))
                           

                           (fact "Check other inputs" :slow
                                 (for-all
                                  [other (gen/such-that #(not (bigint? %))
                                                        (gen/one-of [gen/string gen/boolean gen/uuid])
                                                        100)]
                                  {:num-tests 200}
                                  (fact "A really large number with 16,8 digits"
                                        (let [amount (str other) 
                                              response (h/app
                                                        (->
                                                         (mock/request :post "/wallet/v1/transactions/new")
                                                         (mock/content-type "application/json")
                                                         (mock/body  (cheshire/generate-string (merge
                                                                                                mongo-db-only
                                                                                                {:from-id "from account"
                                                                                                 :to-id "to account"
                                                                                                 :amount amount
                                                                                                 :tags ["blabla"]})))))
                                              body (parse-body (:body response))]
                                          (:status response) => 400
                                          (class (:error body)) => String
                                          (:error body) => "The amount is not valid."))))))
