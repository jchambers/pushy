#!/bin/sh

# Generate a new, self-signed root CA
openssl req -config openssl-custom.cnf -extensions v3_ca -new -x509 -days 36500 -nodes -subj "/CN=PushyTestRoot" -newkey rsa:2048 -sha512 -out ca.crt -keyout ca.key

# Generate a certificate/key for the server
openssl req -new -keyout server.key -nodes -newkey rsa:2048 -subj "/CN=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_server_extensions -req -CAkey ca.key -CA ca.crt -days 36500 -set_serial $RANDOM -sha512 -out server.crt

# Generate certificates/keys for clients and sign them with the intermediate certificate
openssl req -new -keyout single-topic-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_single_topic_client_extensions -req -CAkey ca.key -CA ca.crt -days 36500 -set_serial $RANDOM -sha512 -out single-topic-client.crt

openssl req -new -keyout multi-topic-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_multi_topic_client_extensions -req -CAkey ca.key -CA ca.crt -days 36500 -set_serial $RANDOM -sha512 -out multi-topic-client.crt

# We also want one bogus (self-signed) certificate to make sure the mock server is turning away untrusted clients
openssl req -new -keyout untrusted-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_single_topic_client_extensions -req -days 36500  -set_serial $RANDOM -signkey untrusted-client.key -out untrusted-client.crt

# If we want things to work with both the JDK and OpenSSL providers, we'll the keys in PKCS8 format
openssl pkcs8 -topk8 -nocrypt -in server.key -out server.pk8
openssl pkcs8 -topk8 -nocrypt -in single-topic-client.key -out single-topic-client.pk8
openssl pkcs8 -topk8 -nocrypt -in multi-topic-client.key -out multi-topic-client.pk8
openssl pkcs8 -topk8 -nocrypt -in untrusted-client.key -out untrusted-client.pk8
