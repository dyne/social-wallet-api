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

(def Satoshi 0.00000001)
(def int16-fr8 (BigDecimal. 9999999999999999.99999999) )

(defn new-transaction-request [amount]
  (h/app
   (->
    (mock/request :post "/wallet/v1/transactions/new")
    (mock/content-type "application/json")
    (mock/body  (cheshire/generate-string {:blockchain :mongo
                                           :from-id "test-1"
                                           :to-id "test-2"
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
                                 (let [response (new-transaction-request (str Satoshi))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => Satoshi
                                   (:amount-text body) => (str Satoshi)
                                   (:amount (get-latest-transaction "test-1")) => Satoshi
                                   (:amount-text (get-latest-transaction "test-1")) => (.toString Satoshi)))
                           (fact "16 integer digits and 8 decimal)"
                                 (let [response (new-transaction-request (str int16-fr8))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => int16-fr8
                                   (:amount-text body) => (.toString int16-fr8)
                                   (:amount (get-latest-transaction "test-1")) => int16-fr8
                                   (:amount-text (get-latest-transaction "test-1")) => (.toString int16-fr8)))
                           (fact "Negative amounts not allowed)"
                                 (let [some-negative -16.5
                                       response (new-transaction-request (str some-negative))
                                       body (parse-body (:body response))]
                                   (:status response) => 400
                                   (:error body) => "Negative values not allowed."
                                   (:amount-text body) => nil)))
                    
                    (facts "Check different doubles" :slow
                           (for-all
                            [rand-double (gen/double* {:min Satoshi
                                                       :max int16-fr8
                                                       :NaN? false
                                                       :infinite? false})]
                            {:num-tests 200
                             :seed 1524497634230}
                            (fact "Generative tests"
                                  (let [amount (.toString (BigDecimal. rand-double))  
                                        response (new-transaction-request amount)
                                        body (parse-body (:body response))]
                                    (:status response) => 200
                                    (:amount body) => rand-double
                                    (:amount (get-latest-transaction "test-1")) => (BigDecimal. rand-double)
                                    (:amount-text (get-latest-transaction "test-1")) => amount)))
                           

                           (fact "Check other inputs" :slow
                                 (for-all
                                  [other (gen/one-of [gen/string gen/boolean gen/uuid])]
                                  {:num-tests 200}
                                  (fact "A really large number with 16,8 digits"
                                        (let [amount (log/spy (str other)) 
                                              response (h/app
                                                        (->
                                                         (mock/request :post "/wallet/v1/transactions/new")
                                                         (mock/content-type "application/json")
                                                         (mock/body  (cheshire/generate-string {:blockchain :mongo
                                                                                                :from-id "test-1"
                                                                                                :to-id "test-2"
                                                                                                :amount amount
                                                                                                :tags ["blabla"]}))))
                                              body (parse-body (:body response))]
                                          (:status response) => 400
                                          (class (:error body)) => String
                                          (:error body) => truthy))))))
