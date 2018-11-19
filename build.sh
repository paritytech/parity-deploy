#! /bin/bash
(cd ../parity-ethereum && cargo build -j4) && rm -f docker/parity && cp ../parity-ethereum/target/debug/parity docker && docker build -t parity/clique docker
