#!/bin/sh

# Generate a new, self-signed root CA
openssl req -config openssl-custom.cnf -extensions v3_ca -new -x509 -days 36500 -nodes -subj "/CN=PushyTestRoot" -newkey rsa:2048 -sha512 -out ca.pem -keyout ca.key

# Generate a certificate/key for the server
openssl req -new -keyout server.key -nodes -newkey rsa:2048 -subj "/CN=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_server_extensions -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out server.pem

# Generate certificates/keys for clients and sign them with the intermediate certificate
openssl req -new -keyout single-topic-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_single_topic_client_extensions -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out single-topic-client.pem

openssl req -new -keyout multi-topic-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_multi_topic_client_extensions -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out multi-topic-client.pem

# We also want one bogus (self-signed) certificate to make sure the mock server is turning away untrusted clients
openssl req -new -keyout untrusted-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_single_topic_client_extensions -req -days 36500  -set_serial $RANDOM -signkey untrusted-client.key -out untrusted-client.pem

# For simplicity, squish everything down into PKCS#12 keystores
openssl pkcs12 -export -in server.pem -inkey server.key -out server.p12 -password pass:pushy-test
openssl pkcs12 -export -in single-topic-client.pem -inkey single-topic-client.key -out single-topic-client.p12 -password pass:pushy-test
openssl pkcs12 -export -in multi-topic-client.pem -inkey multi-topic-client.key -out multi-topic-client.p12 -password pass:pushy-test
openssl pkcs12 -export -in untrusted-client.pem -inkey untrusted-client.key -out untrusted-client.p12 -password pass:pushy-test

# We'll also want one keystore that's not password-protected to make sure the no-password constructors behave correctly
openssl pkcs12 -export -in single-topic-client.pem -inkey single-topic-client.key -out single-topic-client-unprotected.p12 -nodes -password pass:

# Clean up intermediate files
rm *.key
rm server.pem single-topic-client.pem multi-topic-client.pem untrusted-client.pem
