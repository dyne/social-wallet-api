(ns social-wallet-api.test.handler
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [social-wallet-api.handler :as h]
            [auxiliary.config :refer [config-read]]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]))

(def test-app-name "social-wallet-api-test")

(def mongo-db-only {:connection "mongo"
                    :type "db-only"})

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

(against-background [(before :contents (h/init
                                        (config-read social-wallet-api.test.handler/test-app-name)
                                        social-wallet-api.test.handler/test-app-name))
                     (after :contents (h/destroy))]
                    (facts "Some basic requests work properly"
                           (fact "Get the label using the blockchain type as string"
                                 (let [response (h/app
                                                 (->
                                                  (mock/request :post "/wallet/v1/label")
                                                  (mock/content-type "application/json")
                                                  (mock/body  (cheshire/generate-string mongo-db-only))))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   body => {:currency "Testcoin"}))

                           (fact "Get the label using the blockchain type as keyword"
                                 (let [response (h/app
                                                 (->
                                                  (mock/request :post "/wallet/v1/label")
                                                  (mock/content-type "application/json")
                                                  (mock/body  (cheshire/generate-string mongo-db-only))))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   body => {:currency "Testcoin"}))
                           (fact "Check that the amount returned after the creation of a transanction in mongo is the same as the input one"
                                 (let [response (h/app
                                                 (->
                                                  (mock/request :post "/wallet/v1/transactions/new")
                                                  (mock/content-type "application/json")
                                                  (mock/body  (cheshire/generate-string (merge
                                                                                         mongo-db-only
                                                                                         {:from-id "test-1"
                                                                                          :to-id "test-2"
                                                                                          :amount "0.1"
                                                                                          :tags ["blabla"]})))))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   (:amount body) => 0.1))))
