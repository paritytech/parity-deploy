#!/bin/bash
# Copyright 2017 Parity Technologies (UK) Ltd.

sed() {
  if [ "$(uname)" = "Darwin" ] ; then
    gsed "$@"
  else
    command sed "$@"
  fi
}

mktemp() {
  if [ "$(uname)" = "Darwin" ]; then
    if [ "$1" = "-p" ]; then
      shift
    fi
    if [ $# == 2 ]; then
      command mktemp $1/$2
    fi
  else
    command mktemp $@
  fi
}

CHAIN_NAME="parity"
CHAIN_NODES="1"
CLIENT="0"
DOCKER_INCLUDE="include/docker-compose.yml"
help()  {

echo "parity-deploy.sh OPTIONS
Usage:
REQUIRED:
        --config dev / aura / tendermint / validatorset / input.json / custom_chain.toml

OPTIONAL:
        --name name_of_chain. Default: parity
        --nodes number_of_nodes (if using aura / tendermint) Default: 2
        --ethstats - Enable ethstats monitoring of authority nodes. Default: Off
        --expose - Expose a specific container on ports 8180 / 8545 / 30303. Default: Config specific

NOTE:
    input.json - Custom spec files can be inserted by specifiying the path to the json file.
    custom_chain.toml - Custom toml file defining multiple nodes. See customchain/config/example.toml for an example.
"

}

check_packages() {

  if [ "$(uname)" = "Darwin" ] ; then
    if [ ! -f /usr/local/bin/docker ] ; then
       brew install docker.io python-pip
    fi

    if [ ! -f /usr/local/bin/docker-compose ] ; then
       brew install docker-compose
    fi
 else if [ $(grep -i debian /etc/*-release | wc -l) -gt 0 ] ; then
     if [ ! -f /usr/bin/docker ] ; then
        sudo apt-get -y install docker.io python-pip
     fi

     if [ ! -f /usr/local/bin/docker-compose ] ; then
        sudo pip install docker-compose
     fi
   fi
  fi
}


genpw() {

openssl rand -base64 12

}


create_node_params() {

local DEST_DIR=deployment/$1
if [ ! -d $DEST_DIR ] ; then
   mkdir -p $DEST_DIR
fi

echo '' > $DEST_DIR/password
./config/utils/keygen.sh $DEST_DIR

local SPEC_FILE=$(mktemp -p $DEST_DIR spec.XXXXXXXXX)
sed "s/CHAIN_NAME/$CHAIN_NAME/g" config/spec/example.spec > $SPEC_FILE
parity --chain $SPEC_FILE --keys-path $DEST_DIR/ account new --password $DEST_DIR/password  > $DEST_DIR/address.txt
rm $SPEC_FILE

echo "NETWORK_NAME=$CHAIN_NAME" > .env

}

create_reserved_peers_poa() {

  PUB_KEY=`cat deployment/$1/key.pub`
  echo "enode://$PUB_KEY@host$1:30303" >> deployment/chain/reserved_peers
}


create_reserved_peers_instantseal() {

   PUB_KEY=`cat deployment/$1/key.pub`
     echo "enode://$PUB_KEY@127.0.0.1:30303" >> deployment/chain/reserved_peers

}


build_spec() {

 display_header
 display_name
 display_engine
 display_params
 display_genesis
 display_accounts
 display_footer

}


build_docker_config_poa() {

 echo "version: '2.0'" > docker-compose.yml
 echo "services:" >> docker-compose.yml

 for x in ` seq 1 $CHAIN_NODES ` ; do
    cat config/docker/authority.yml | sed -e "s/NODE_NAME/$x/g" | sed -e "s@-d /parity/data@-d /parity/data $PARITY_OPTIONS@g" >> docker-compose.yml
 done

 cat $DOCKER_INCLUDE >> docker-compose.yml

}

build_docker_config_ethstats() {

 if [ "$ETHSTATS" == "1" ] ; then
    cat include/ethstats.yml >> docker-compose.yml
 fi
}


build_docker_config_instantseal() {

  cat config/docker/instantseal.yml | sed -e "s@-d /parity/data@-d /parity/data $PARITY_OPTIONS@g" > docker-compose.yml

}


build_docker_client() {

  if [ "$CLIENT" == "1" ]; then
    create_node_params client
    cp config/spec/client.toml deployment/client/
    cat config/docker/client.yml >> docker-compose.yml

    # writing client dependencies
    if [ "$CHAIN_NODES" -gt "0" ] ; then
      echo "       depends_on:" >> docker-compose.yml

      for x in ` seq 1 $CHAIN_NODES ` ; do
        echo "       - \"host${x}\"" >> docker-compose.yml
      done
    fi
  fi
}


build_custom_chain() {


  if [ -z "$CUSTOM_CHAIN" ] ; then
     echo "Must specify argument for custom chain option."
     exit 1
  fi

  ./customchain/generate.py "$CUSTOM_CHAIN"

  exit 0
}

display_header() {

  cat config/spec/chain_header

}

display_footer() {

  cat config/spec/chain_footer
}


display_name() {

 cat config/spec/name | sed -e "s/CHAIN_NAME/$CHAIN_NAME/g"
}


create_node_config_poa() {

  ENGINE_SIGNER=`cat deployment/$1/address.txt`
  cat config/spec/authority_round.toml | sed -e "s/ENGINE_SIGNER/$ENGINE_SIGNER/g" > deployment/$1/authority.toml

}

create_node_config_instantseal() {

  ENGINE_SIGNER=`cat deployment/$1/address.txt`
  cat config/spec/instant_seal.toml | sed -e "s/ENGINE_SIGNER/$ENGINE_SIGNER/g" > deployment/$1/authority.toml

 }

expose_container() {
  sed -i "s@container_name: $1@&\n       ports:\n       - 8080:8080\n       - 8180:8180\n       - 8545:8545\n       - 8546:8546\n       - 30303:30303@g" docker-compose.yml

}

select_exposed_container() {

  if [ -n "$EXPOSE_CLIENT" ] ; then
    expose_container $EXPOSE_CLIENT
  else
    if [ "$CLIENT" == "0" ] ; then
    expose_container host1
    fi
  fi

}

display_engine() {

 case $CHAIN_ENGINE in
      dev)
	cat config/spec/engine/instantseal
	;;
      aura|validatorset|tendermint)
	for x in ` seq 1 $CHAIN_NODES ` ; do
           VALIDATOR=`cat deployment/$x/address.txt`
	   RESERVED_PEERS="$RESERVED_PEERS $VALIDATOR"
	   VALIDATORS="$VALIDATORS \"$VALIDATOR\","
	done
        # Remove trailing , from validator list
        VALIDATORS=`echo $VALIDATORS | sed 's/\(.*\),.*/\1/'`
	cat config/spec/engine/$CHAIN_ENGINE | sed -e "s/0x0000000000000000000000000000000000000000/$VALIDATORS/g"
	;;
	*)
	echo "Unknown engine: $CHAIN_ENGINE"
 esac

}

display_params() {


  cat config/spec/params/$CHAIN_ENGINE

}

display_genesis() {


  cat config/spec/genesis/$CHAIN_ENGINE

}


display_accounts() {


  cat config/spec/accounts/$CHAIN_ENGINE

}

ARGS="$@"

while [ "$1" != "" ]; do
    case $1 in
         --name)                shift
                                CHAIN_NAME=$1
                                ;;
        -c | --config )         shift
                                CHAIN_ENGINE=$1
                                ;;
        -n | --nodes )          shift
                                CHAIN_NODES=$1
                                ;;
        -r | --release)         shift
                                PARITY_RELEASE=$1
                                ;;
        -e | --ethstats)        ETHSTATS=1
                                ;;
        --enable-client)        CLIENT=1
                                ;;
        --expose)               shift
                                EXPOSE_CLIENT="$1"
                                ;;
        --chain)                shift
                                CHAIN_NETWORK=$1
                                ;;
        -h | --help )           help
                                exit
                                ;;
        *)                    	PARITY_OPTIONS="$PARITY_OPTIONS $1 "
    esac
    shift
done

if [ -z "$CHAIN_ENGINE" ] && [ -z "$CHAIN_NETWORK" ]; then
    echo "No chain argument, exiting..."
    exit 1
fi



# Get a copy of the parity binary, overwriting if release is set

if [ ! -f /usr/bin/parity ] || [ -n "$PARITY_RELEASE" ] ; then

        if [ -z "$PARITY_RELEASE" ] ; then
                echo "NO custom parity build set, downloading stable"
                bash <(curl https://get.parity.io -Lk -r stable)
        else
                echo "Custom parity build set: $PARITY_RELEASE"
                curl -o parity-download.sh https://get.parity.io -Lk
                bash parity-download.sh -r $PARITY_RELEASE
        fi
fi


mkdir -p deployment/chain
check_packages

echo $CHAIN_ENGINE | grep -q toml
if [ $? -eq 0 ] ; then
   ./customchain/generate.py "$CHAIN_ENGINE"
   exit 0
fi

if [ ! -z "$CHAIN_NETWORK" ]; then
  if [ ! -z "$PARITY_OPTIONS" ]; then
      cat config/docker/chain.yml | sed -e "s/CHAIN_NAME/$CHAIN_NETWORK/g" | sed -e "s@-d /parity/data@-d /parity/data $PARITY_OPTIONS@g"  > docker-compose.yml

  else
      cat config/docker/chain.yml | sed -e "s/CHAIN_NAME/$CHAIN_NETWORK/g"  > docker-compose.yml
  fi

elif [ "$CHAIN_ENGINE" == "dev" ] ; then
   echo "using instantseal"
   create_node_params is_authority
   create_reserved_peers_instantseal is_authority
   create_node_config_instantseal is_authority
   build_docker_config_instantseal

elif [ "$CHAIN_ENGINE" == "aura" ] || [ "$CHAIN_ENGINE" == "validatorset" ] || [ "$CHAIN_ENGINE" == "tendermint" ] || [ -f "$CHAIN_ENGINE" ] ; then
  if [ $CHAIN_NODES ] ; then
     for x in ` seq $CHAIN_NODES ` ; do
        create_node_params $x
        create_reserved_peers_poa $x
        create_node_config_poa $x
     done
     build_docker_config_poa
     build_docker_client
  fi

  if [ "$CHAIN_ENGINE" == "aura" ] || [ "$CHAIN_ENGINE" == "validatorset" ] || [ "$CHAIN_ENGINE" == "tendermint" ] ; then
     build_spec > deployment/chain/spec.json
  else
     mkdir -p deployment/chain
     cp $CHAIN_ENGINE deployment/chain/spec.json
  fi


else
	echo "Could not find spec file: $CHAIN_ENGINE"
fi

build_docker_config_ethstats
select_exposed_container
