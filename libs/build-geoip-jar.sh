#!/usr/bin/env sh

BASE=$(dirname "$0")

cd "$BASE" || exit

rm -rf geoip.jar assets

mkdir assets
cd assets || exit
wget https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip
wget https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip6
cd ..

zip -9 -o geoip.jar assets/geoip assets/geoip6
rm -rf assets
