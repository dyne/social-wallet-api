;; Social Wallet REST API

;; Copyright (C) 2018- Dyne.org foundation

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

(ns social-wallet-api.test.api-key
  (:require [midje.sweet :refer :all]
            [social-wallet-api.api-key :as ak]
            [social-wallet-api.handler :as h]
            [clj-storage.core :as store]
            [auxiliary.config :refer [config-read]]
            [social-wallet-api.test.handler :refer [test-app-name]]
            [taoensso.timbre :as log]))

(defn empty-apikeys []
  (store/delete-all! (-> @h/connections :mongo :stores-m :apikey-store)))

(against-background [(before :contents (h/init
                                        (config-read test-app-name)
                                        test-app-name))
                     (after :contents (do
                                        (empty-apikeys)
                                        (h/destroy)))]
                    (let [apikey-store (-> @h/connections :mongo :stores-m :apikey-store)]
                      (facts "Test that API key creation works and doesnt allow for duplicates."
                             (:client-app (ak/create-and-store-apikey! apikey-store "app-1" 32)) => "app-1"
                             (.startsWith
                              (:message (ak/create-and-store-apikey! apikey-store "app-1" 32))
                              "Could not create api-key entry because:") => true

                             (:client-app (ak/create-and-store-apikey! apikey-store "app-2" 32)) => "app-2")))
