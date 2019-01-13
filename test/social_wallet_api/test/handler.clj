;; Social Wallet REST API

;; Copyright (C) 2017- Dyne.org foundation

;; Sourcecode designed, written and maintained by
;; Aspasia Beneti <aspra@dyne.org>

;; This file is part of Social Wallet REST API.

;; Social Wallet REST API is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

;; Social Wallet REST API is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.

;; Additional permission under GNU AGPL version 3 section 7.

;; If you modify Social Wallet REST API, or any covered work, by linking or combining it with any library (or a modified version of that library), containing parts covered by the terms of EPL v 1.0, the licensors of this Program grant you additional permission to convey the resulting work. Your modified version must prominently offer all users interacting with it remotely through a computer network (if your version supports such interaction) an opportunity to receive the Corresponding Source of your version by providing access to the Corresponding Source from a network server at no charge, through some standard or customary means of facilitating copying of software. Corresponding Source for a non-source form of such a combination shall include the source code for the parts of the libraries (dependencies) covered by the terms of EPL v 1.0 used as well as that of the covered work.

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
                                        (config-read test-app-name)
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
