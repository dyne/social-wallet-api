(ns social-wallet-api.test.schema
  (:require [midje.sweet :refer :all]
            [social-wallet-api.schema :refer [AccountDetails BTCTransaction]]
            [schema.core :as s]
            [cheshire.core :as json]))

(fact "Check some schemas"
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/sample-btc-transaction-response.json")))  => truthy)
