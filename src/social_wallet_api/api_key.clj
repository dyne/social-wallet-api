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


(ns social-wallet-api.api-key
  (:require [taoensso.timbre :as log]
            [fxc.core :as fxc]
            [failjure.core :as f]
            [freecoin-lib.db.api-key :as ak]))

(defonce apikey (atom {}))

(defn- generate-apikey [length]
  (fxc/generate length))

(defn create-and-store-apikey! [apikey-store client-app length]
  (f/if-let-ok? [apikey-entry (f/try* (ak/create-apikey! {:apikey-store apikey-store
                                                          :client-app client-app
                                                          :api-key (generate-apikey length)}))]
    apikey-entry
    (f/fail (str "Could not create api-key entry because: " apikey-entry))))

(defn fetch-apikey [apikey-store client-app]
  "Fetches the api key from storage and returns it, retuns nil if not found."
  (ak/fetch-by-client-app apikey-store client-app))

(defn write-apikey-file [file apikey]
  (spit file apikey))
