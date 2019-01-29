# Changelog

## social-wallet-api 1.3.0
* Added licencing and README file with template.

## social-wallet-api 1.2
* Added the option to require an API KEY for every requests. An API KEY is produced per client app. 	
	
## social-wallet-api 1.1

* Dockerized SWAPI together with goodies like mongo-express (mongo UI)

## social-wallet-api 1.0

* Upgraded to Clojure 1.9
* Support Cross-Origin Requests
* Replaced parameter `blockchain` with the composite `connection`  and `type`
* Timestamps now stored as dates rather than strings
* Added transaction description and currency parameters
* Filter transactions by dates
* Filter transactions by tags

## social-wallet-api 0.10.0
* Added pagination to transaction/list

## social-wallet-api 0.9.4
* Jetty host can be picked up as a env var
	
## social-wallet-api 0.9.3
* Arithmetic precision testing and fixes

## social-wallet-api 0.9.2
* Withdrawl and deposit implementation
	
## social-wallet-api 0.8.0
* Extracted the authentication to separate lib

## social-wallet-api 0.7.0
* Blockchain transaction fees get updated on DB after confirmed	
	
## social-wallet-api 0.6.0
* First stable release that supports both DB and Blockchains
