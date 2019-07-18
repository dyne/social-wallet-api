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

(ns social-wallet-api.test.api-key
  (:require [midje.sweet :refer [against-background before after facts fact =>]]
            [social-wallet-api.api-key :as ak]
            [social-wallet-api.handler :as h]
            [social-wallet-api.core :as swapi]
            [clj-storage.core :as store]
            [auxiliary.config :refer [config-read]]
            [taoensso.timbre :as log]
            [ring.mock.request :as mock]
            [cheshire.core :as cheshire]))

(def test-app-name "social-wallet-with-apikey-test")
(def mongo-db-only social-wallet-api.test.handler/mongo-db-only)
(def parse-body social-wallet-api.test.handler/parse-body)

(defn empty-apikeys []
  (store/delete-all! (-> @swapi/connections :mongo :stores-m :apikey-store)))

(against-background [(before :contents (swapi/init
                                        (config-read test-app-name)
                                        test-app-name))
                     (after :contents (do
                                        (empty-apikeys)
                                        (swapi/destroy)))]
                    (let [apikey-store (-> @swapi/connections :mongo :stores-m :apikey-store)]
                      (facts "Test that API key creation works and doesnt allow for duplicates."
                             (:client-app (ak/create-and-store-apikey! apikey-store "app-1" 32)) => "app-1"
                             (.startsWith
                              (:message (ak/create-and-store-apikey! apikey-store "app-1" 32))
                              "Could not create api-key entry because:") => true

                             (:client-app (ak/create-and-store-apikey! apikey-store "app-2" 32)) => "app-2")
                      (facts "Test that the API KEY works with requests."
                             (fact "A request sent without the API KEY on the headers doesnt work."
                                   (let [response (h/app
                                                   (->
                                                    (mock/request :post "/wallet/v1/label")
                                                    (mock/content-type "application/json")
                                                    (mock/body  (cheshire/generate-string mongo-db-only))))
                                         body (parse-body (:body response))]
                                     (:status response) => 401
                                     body => {:error "Could not access the Social Wallet API: wrong API KEY"}))
                             (fact "A request sent with the wrong API KEY on the headers doesnt work."
                                   (let [response (h/app
                                                   (->
                                                    (mock/request :post "/wallet/v1/label")
                                                    (mock/content-type "application/json")
                                                    (mock/header "X-API-Key" "wrong-key")
                                                    (mock/body  (cheshire/generate-string mongo-db-only))))
                                         body (parse-body (:body response))]
                                     (:status response) => 401
                                     body => {:error "Could not access the Social Wallet API: wrong API KEY"}))
                             (fact "A request sent with the correct API KEY on the headers works as expected."
                                   (let [apikey (get @ak/apikey @swapi/client)
                                         response (h/app
                                                   (->
                                                    (mock/request :post "/wallet/v1/label")
                                                    (mock/content-type "application/json")
                                                    (mock/header "X-API-Key" apikey)
                                                    (mock/body  (cheshire/generate-string mongo-db-only))))
                                         body (parse-body (:body response))]
                                     (:status response) => 200
                                     body => {:currency "Testcoin"})))))
