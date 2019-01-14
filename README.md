# Social Wallet API

The Social Wallet API  is made to facilitate the integration of blockchain
functions into existing front-end applications, providing an easy
backend of documented REST API endpoints that are validated and, in
case of error, report meaningful messages. 
It provides with an abstraction that creates, adds metadata, lists
and filters tranactions.

The Social Wallet API allows to make calls to mongo and to running
blockchain nodes that are compatibile with Bitcoin Core and support
the generic Bitcoin RPC. Overtime more implemenations will be provided for
different blockchains and storages.

<a href="https://www.dyne.org"><img
src="https://secrets.dyne.org/static/img/swbydyne.png"
alt="software by Dyne.org"
title="software by Dyne.org" class="pull-right"></a>

[Getting started](#Getting-Started) | [Prerequisites](#Prerequisites) | [Running](#Running-the-Social-Wallet) | [Running the tests](#Running-the-tests) | [Deployment](#Deployment) | [Acknowledgements](#Acknowledgements) | [Licence](#Licence) | [change log](https://github.com/Commonfare-net/social-wallet-api/blob/master/CHANGELOG.markdown) 

[![Build Status](https://travis-ci.org/Commonfare-net/social-wallet-api.svg?branch=master)](https://travis-ci.org/Commonfare-net/social-wallet-api)
[![Clojars Project](https://img.shields.io/clojars/v/social-wallet-api.svg)](https://clojars.org/social-wallet-api)
[![Freecoin project stats](https://www.openhub.net/p/freecoin/widgets/project_thin_badge.gif)](https://www.openhub.net/p/freecoin)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

This REST API interface is so far meant for low-level access of
wallets built using the [Freecoin toolkit](https://freecoin.dyne.org).

[![Freecoin.dyne.org](https://freecoin.dyne.org/images/freecoin_logo.png)](https://freecoin.dyne.org)


## Getting Started

<img class="pull-right"
src="https://secrets.dyne.org/static/img/clojure.png">

The Social Wallet API is written in Clojure and is fully
cross-platform: one can run it locally on a GNU/Linux machine, as well
on Apple/OSX and MS/Windows.

### Prerequisites

<img class="pull-left" src="https://secrets.dyne.org/static/img/leiningen.jpg"
style="padding-right: 1.5em">

Please install
1. A JDK. The software is tested on [openJDK](http://openjdk.java.net/) versions 7 and 8 as well as with [oracleJDK 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) and 10. Make sure that the env var JAVA_HOME is set to the JDK install dir like [mentioned here](https://docs.oracle.com/cd/E19182-01/820-7851/inst_cli_jdk_javahome_t/index.html).
2. [MongoDB community edition](https://docs.mongodb.com/manual/administration/install-community/). The software has been tested on Mongo v3.6.4. Earlier versions might not work due to loss of precision (Decimal128 was not introduced).
3. [leiningen](https://leiningen.org/) which is used for dependency management like:
```
mkdir ~/bin
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -O ~/bin/lein
chmod +x ~/bin/lein
```

For instance on Devuan systems one can install all necessary
dependencies using apt and the following packages: `apt-get
openjdk-7-jdk libversioneer-clojure haveged`.

### Running the Social Wallet API

First of all check the configuration in
`resources/social-wallet-api.yaml` and adjust its contents to your
setup. Here an example complete with comments:

```yaml
# verbosity level of messages
log-level: debug

# open freecoin specific section
freecoin:
# indentation matters: mind the initial spaces of following sections
  # If an apikey is added to the config, for that particular installation an apikey will be created, stored and required with every request. Device-id should be replaced with the device/client-app name/id.
  apikey: device-id
# configuration for the database holding local transactions
  mongo:
    host: localhost
    port: 27017
    db:   freecoin
    currency: Commoncoin # You can set this to the name of the local currency to be used on the DB

# configuration of the 'faircoin' blockchain
  faircoin:
# visualised name of the currency
    currency: fair
# number of confirmations to consider a transaction as valid
    number-confirmations: 6
# frequency of confirmations checks in milliseconds
    frequency-confirmations-millis: 20000
# path to the rpc configuration holding username and password
    rpc-config-path: /home/user/.faircoin2/faircoin.conf
# deposit to an address watch expiration time in milliseconds
    deposit-expiration-millis: 3600000
# frequency of deposit checks in milliseconds
    frequency-deposit-millis: 60000

# configuration of the 'bitcoin' blockchain
  bitcoin:
# visualised name of the currency
    currency: btc
# number of confirmations to consider a transaction as valid
    number-confirmations: 6
# frequency of confirmations checks in milliseconds
    frequency-confirmations-millis: 20000
# path to the rpc configuration holding username and password
    rpc-config-path: /home/user/.bitcoin/bitcoin.conf
```

Once correctly configured, from inside the social-wallet-api source
directory one can use various commands to run it live (refreshing live
changes to the code) using:

- `lein ring server` (which will start and spawn a browser on it)
- `lein ring server-headless` (will start without browser)

One can also use `lein uberjar` to build a standalone jar application,
or `lein uberwar` to build a standalone war application ready to be
served from enterprise infrastructure using JBoss or Tomcat.

## Running the tests

To run all tests one need to run
` lein midje`
on the project dir

## Deployment

Even though there are many ways to deploy this software, a dockerised version is provided in this project. 
For more details look into the [docker](docker) folder.

## TODO

- Multicurrency on the DB

## Acknowledgements

The Social Wallet API is Free and Open Source research and development
activity funded by the European Commission in the context of
the
[Collective Awareness Platforms for Sustainability and Social Innovation (CAPSSI)](https://ec.europa.eu/digital-single-market/en/collective-awareness) program. Social
Wallet API uses the
underlying [Freecoin-lib](https://github.com/dyne/freecoin-lib)
blockchain implementation library and adopted as a component of the
social wallet toolkit being developed for
the [Commonfare project](https://pieproject.eu) (grant nr. 687922) .


## License

This project is licensed under the AGPL 3 License - see the [LICENSE](LICENSE) file for details
