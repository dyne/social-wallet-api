(ns social-wallet-api.test.handler
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [social-wallet-api.handler :as h]
            [auxiliary.config :refer [config-read]]
            [taoensso.timbre :as log]
            [cheshire.core :as cheshire]))

(def test-app-name "social-wallet-api-test")

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
                                                  (mock/body  (cheshire/generate-string {:blockchain "mongo"}))))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   body => "MONGO"))

                           (fact "Get the label using the blockchain type as keyword"
                                 (let [response (h/app
                                                 (->
                                                  (mock/request :post "/wallet/v1/label")
                                                  (mock/content-type "application/json")
                                                  (mock/body  (cheshire/generate-string {:blockchain :mongo}))))
                                       body (parse-body (:body response))]
                                   (:status response) => 200
                                   body => "MONGO"))))
