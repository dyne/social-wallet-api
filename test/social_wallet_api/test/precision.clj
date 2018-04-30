(ns social-wallet-api.test.precision
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [social-wallet-api.handler :as h]
            [auxiliary.config :refer [config-read]]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]
            [clojure.test.check.generators :as gen]
            [midje.experimental :refer [for-all]]
            [freecoin-lib.core :as lib]))

(def test-app-name "social-wallet-api-test")

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(def Satoshi (BigDecimal. "0.00000001"))
(def int16-fr8 (BigDecimal. "9999999999999999.99999999"))

(def from-account "test-prec-1")
(def to-account "test-prec-2")

(defn new-transaction-request [amount]
  (h/app
   (->
    (mock/request :post "/wallet/v1/transactions/new")
    (mock/content-type "application/json")
    (mock/body  (cheshire/generate-string {:blockchain :mongo
                                           :from-id from-account
                                           :to-id to-account
                                           :amount amount
                                           :tags ["blabla"]})))))

(defn get-latest-transaction [account-id]
  (first (lib/list-transactions (:mongo @h/blockchains) {:account-id account-id})))

(against-background [(before :contents (h/init
                                        (config-read social-wallet-api.test.handler/test-app-name)
                                        social-wallet-api.test.handler/test-app-name))
                     (after :contents (h/destroy))]
                    (facts "Check specific amounts" 
                           (fact "Check one Satochi (8 decimal)"
                                 (let [response (new-transaction-request (.toString Satoshi))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => Satoshi
                                   (:amount-text body) => (str Satoshi)
                                   (:amount (get-latest-transaction from-account)) => Satoshi
                                   (class (:amount (get-latest-transaction from-account))) => java.math.BigDecimal
                                   (:amount-text (get-latest-transaction from-account)) => (.toString Satoshi)))
                           (fact "16 integer digits and 8 decimal)"
                                 (let [response (new-transaction-request (str int16-fr8))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => int16-fr8
                                   (:amount-text body) => (.toString int16-fr8)
                                   (:amount (get-latest-transaction from-account)) => int16-fr8
                                   (class (:amount (get-latest-transaction from-account))) => java.math.BigDecimal                    
                                   (:amount-text (get-latest-transaction from-account)) => (.toString int16-fr8)))
                           (fact "Negative amounts not allowed)"
                                 (let [some-negative -16.5
                                       response (new-transaction-request (str some-negative))
                                       body (parse-body (:body response))]
                                   (:status response) => 400
                                   (:error body) => "Negative values not allowed."
                                   (:amount-text body) => nil)))
                    
                    (facts "Check different doubles" :slow
                           (let [sum-to-account (atom (BigDecimal. 0))]
                             (for-all
                              [rand-double (gen/double* {:min Satoshi
                                                         :max int16-fr8
                                                         :NaN? false
                                                         :infinite? false})]
                              {:num-tests 100
                               :seed 1525087600100}
                              (fact "Generative tests"
                                    (let [amount (.toString rand-double)  
                                          response (new-transaction-request amount)
                                          body (parse-body (:body response))
                                          _ (swap! sum-to-account #(.add % (BigDecimal. amount)))]
                                      (:status response) => 200
                                      ;; To create a BigDecimal parse from string otherwise
                                      ;; (BigDecimal.  0.5000076293945312) => 0.50000762939453125M
                                      ;; whereas:  "0.5000076293945312") =>   0.5000076293945312M
                                      (BigDecimal. (str (:amount body))) => (BigDecimal. amount)
                                      (-> (get-latest-transaction from-account)
                                                   :amount
                                                   .toString
                                                   BigDecimal.) => (BigDecimal. amount) 
                                      (:amount-text (get-latest-transaction from-account)) => amount)))
                             #_(fact "Balance works properly"
                                   (let [response (h/app
                                                   (->
                                                    (mock/request :post "/wallet/v1/balance")
                                                    (mock/content-type "application/json")
                                                    (mock/body  (cheshire/generate-string {:blockchain :mongo
                                                                                           :account-id to-account}))))
                                         body (parse-body (:body response))]
                                     (:amount body) => @sum-to-account)))
                           

                           #_(fact "Check other inputs" :slow
                                 (for-all
                                  [other (gen/one-of [gen/string gen/boolean gen/uuid])]
                                  {:num-tests 200}
                                  (fact "A really large number with 16,8 digits"
                                        (let [amount (str other) 
                                              response (h/app
                                                        (->
                                                         (mock/request :post "/wallet/v1/transactions/new")
                                                         (mock/content-type "application/json")
                                                         (mock/body  (cheshire/generate-string {:blockchain :mongo
                                                                                                :from-id from-account
                                                                                                :to-id to-account
                                                                                                :amount amount
                                                                                                :tags ["blabla"]}))))
                                              body (parse-body (:body response))]
                                          (:status response) => 400
                                          (class (:error body)) => String
                                          (:error body) => truthy))))))
