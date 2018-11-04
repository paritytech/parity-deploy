#! /bin/bash
set -e
rm -rf data && mkdir data
./parity account import --chain spec.json  parity-keys/clique/UTC--2018-11-03T19-21-22Z--c958cf37-1034-2fb9-b914-f6948a5f13d1
./parity  --chain spec.json --config authority.toml -d ./data  -l engine=trace --force-sealing --reseal-min-period 10
