FROM ubuntu:bionic

ADD parity /

RUN apt update -y && apt install -y libc6-dev

ENTRYPOINT ["/parity"]
