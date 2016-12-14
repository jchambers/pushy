#!/bin/sh

# Generate a new, self-signed root CA
openssl req -extensions v3_ca -new -x509 -days 36500 -nodes -subj "/CN=PushyTestRoot" -newkey rsa:2048 -sha512 -out ca.pem -keyout ca.key

# Generate a certificate/key for the server
openssl req -new -keyout server_key.pem -nodes -newkey rsa:2048 -subj "/CN=com.relayrides.pushy" | \
    openssl x509 -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out server_certs.pem

# Generate a private key for token authentication testing
openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out token-auth-private-key.p8

# Clean up intermediate files
rm ca.key
