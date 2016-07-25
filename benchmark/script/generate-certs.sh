#!/bin/sh

# Generate a new, self-signed root CA
openssl req -extensions v3_ca -new -x509 -days 36500 -nodes -subj "/CN=PushyTestRoot" -newkey rsa:2048 -sha512 -out ca.pem -keyout ca.key

# Generate a certificate/key for the server
openssl req -new -keyout server_key.pem -nodes -newkey rsa:2048 -subj "/CN=com.relayrides.pushy" | \
    openssl x509 -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out server_certs.pem

# Generate certificates/keys for clients and sign them with the intermediate certificate
openssl req -new -keyout client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out client.pem

# For convenience, squish the client credentials down into a PKCS#12 keystore
openssl pkcs12 -export -in client.pem -inkey client.key -out client.p12 -password pass:pushy-test

# Clean up intermediate files
rm ca.key
rm client.key
rm client.pem