#!/usr/bin/env sh

BASE=$(dirname "$0")

cd "$BASE" || exit

rm -rf assets

mkdir assets
cd assets || exit
wget https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip
wget https://gitlab.torproject.org/tpo/core/tor/-/raw/main/src/config/geoip6
cd ..

python3 reproducible_zip.py assets geoip_new.jar

sha256sum geoip.jar geoip_new.jar
rm -f geoip.jar
mv geoip_new.jar geoip.jar

rm -rf assets
