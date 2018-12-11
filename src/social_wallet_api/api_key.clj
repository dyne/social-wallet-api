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

(ns social-wallet-api.api-key
  (:require [taoensso.timbre :as log]
            [fxc.core :as fxc]
            [failjure.core :as f]
            [freecoin-lib.db.api-key :as ak]))

(defonce apikey (atom ""))

(defn- generate-apikey [length]
  (fxc/generate length))

(defn create-and-store-apikey! [apikey-store client-app length]
  (f/if-let-ok? [apikey-entry (f/try* (ak/create-apikey! {:apikey-store apikey-store
                                                          :client-app client-app
                                                          :api-key (generate-apikey length)}))]
    apikey-entry
    (f/fail (str "Could not create api-key entry because: " apikey-entry))))

(defn apikey? [apikey-store client-app]
  (ak/fetch-by-client-app apikey-store client-app))
