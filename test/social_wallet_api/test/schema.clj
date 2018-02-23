(ns social-wallet-api.test.schema
  (:require [midje.sweet :refer :all]
            [social-wallet-api.schema :refer [AccountDetails BTCTransaction
                                              DecodedRawTransaction]]
            [schema.core :as s]
            [cheshire.core :as json]))

(fact "Check some schemas"
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/sample-btc-transaction-response.json")))  => truthy
      
      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/confirmed-transaction-faircoin.json")))  => truthy

      (s/validate BTCTransaction (json/parse-string (slurp "test-resources/not-confirmed-transaction-faircoin.json")))  => truthy

      (s/validate DecodedRawTransaction (json/parse-string (slurp "test-resources/sample-decoded-raw-transaction.json"))) => truthy)
