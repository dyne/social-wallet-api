# Social Wallet API

<a href="https://www.dyne.org"><img
src="https://secrets.dyne.org/static/img/swbydyne.png"
alt="software by Dyne.org"
title="software by Dyne.org" class="pull-right"></a>

This software is made to facilitate the integration of blockchain
functions into existing front-end applications, providing an easy
backend of documented REST API endpoints that are validated and, in
case of error, report meaningful messages.

[![Build Status](https://travis-ci.org/Commonfare-net/social-wallet-api.svg?branch=master)](https://travis-ci.org/Commonfare-net/social-wallet-api)

This REST API interface is so far meant for low-level access of
wallets built using the [Freecoin toolkit](https://freecoin.dyne.org).

[![Freecoin.dyne.org](https://freecoin.dyne.org/images/freecoin_logo.png)](https://freecoin.dyne.org)

The Social Wallet API allows to make calls to mongo and to running
blockchain nodes that are compatibile with Bitcoin Core and support
the generic Bitcoin RPC.

## Building the Social Wallet API on your own computer

<img class="pull-right"
src="https://secrets.dyne.org/static/img/clojure.png">

The Social Wallet API is written in Clojure and is fully
cross-platform: one can run it locally on a GNU/Linux machine, as well
on Apple/OSX and MS/Windows.

<img class="pull-left" src="https://secrets.dyne.org/static/img/leiningen.jpg"
style="padding-right: 1.5em">

The following software is
required:
[OpenJDK](http://openjdk.java.net),
[Clojure](http://clojure.org), [Leiningen](http://leiningen.org).

For instance on Devuan systems one can install all necessary
dependencies using apt and the following packages: `apt-get
openjdk-7-jdk libversioneer-clojure haveged`.

then install Leiningen which will take care of all Clojure dependencies:

```
mkdir ~/bin
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -O ~/bin/lein
chmod +x ~/bin/lein
```

## Running the Social Wallet API

First of all check the configuration in
`resources/social-wallet-api.yaml` and adjust its contents to your
setup. Here an example complete with comments:

```yaml
# verbosity level of messages
log-level: debug

# open freecoin specific section
freecoin:
# indentation matters: mind the initial spaces of following sections

# configuration for the database holding local transactions
  mongo:
    host: localhost
    port: 27017
    db:   freecoin

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

Social Wallet API is Copyright (C) 2017 by the Dyne.org Foundation

This software and its documentation are designed, written and maintained
by Denis Roio <jaromil@dyne.org> and Aspasia Beneti <aspra@dyne.org>

```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
