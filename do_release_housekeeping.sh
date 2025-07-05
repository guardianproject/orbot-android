#!/usr/bin/env sh

BASE=$(dirname "$0")

# Update geoip files from tor.
sh "$BASE/libs/build-geoip-jar.sh"

# Update built-in bridges.
sh "$BASE/scripts/update-bridges.sh"
