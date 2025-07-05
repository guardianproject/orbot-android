#!/usr/bin/env sh

BASE=$(dirname "$0")

cd "$BASE/../orbotservice/src/main/assets" || exit

rm -f builtin-bridges.json

wget -O builtin-bridges.json https://bridges.torproject.org/moat/circumvention/builtin
