(ns social-wallet-api.test.handler
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as request]
            [social-wallet-api.handler :as h]
            [auxiliary.config :refer [config-read]]
            [taoensso.timbre :as log]))
(def app-name "social-wallet-api-test")
(against-background [(before :contents (h/init (config-read app-name) app-name))
                     (after :contents (h/destroy))]
                    (facts "Some basic requests work properly"
                           (fact "Get the label"
                                 (let [response (h/app (request/request :post "/label" {}))]
                                   (:status response) => 200))))
