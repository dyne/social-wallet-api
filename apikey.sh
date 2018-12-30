#!/bin/bash

if [ $# -lt 1 ]
then
  echo "Usage: $0 <client-app>"
  exit 1
fi

mongo localhost/freecoin --eval "db['apikey-store'].find({'client-app':'$1'});"
