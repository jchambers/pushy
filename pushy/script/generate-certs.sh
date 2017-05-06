#!/bin/sh

# Generate a new, self-signed root CA
openssl req -config openssl-custom.cnf -extensions v3_ca -new -x509 -days 36500 -nodes -subj "/CN=PushyTestRoot" -newkey rsa:2048 -sha512 -out ca.pem -keyout ca.key

# Generate a certificate/key for the server
openssl req -new -keyout server-key.pem -nodes -newkey rsa:2048 -subj "/CN=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_server_extensions -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out server-certs.pem

# Generate certificates/keys for clients and sign them with the intermediate certificate
openssl req -new -keyout single-topic-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_single_topic_client_extensions -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out single-topic-client.pem

openssl req -new -keyout multi-topic-client.key -nodes -newkey rsa:2048 -subj "/CN=Apple Push Services: com.relayrides.pushy/UID=com.relayrides.pushy" | \
    openssl x509 -extfile ./apns-extensions.cnf -extensions apns_multi_topic_client_extensions -req -CAkey ca.key -CA ca.pem -days 36500 -set_serial $RANDOM -sha512 -out multi-topic-client.pem

# For simplicity, squish everything down into PKCS#12 keystores
openssl pkcs12 -export -in server-certs.pem -inkey server-key.pem -out server.p12 -password pass:pushy-test
openssl pkcs12 -export -in single-topic-client.pem -inkey single-topic-client.key -out single-topic-client.p12 -password pass:pushy-test
openssl pkcs12 -export -in multi-topic-client.pem -inkey multi-topic-client.key -out multi-topic-client.p12 -password pass:pushy-test

# We'll also want one keystore with an unprotected key to make sure no-password constructors behave correctly
openssl pkcs12 -export -in single-topic-client.pem -inkey single-topic-client.key -out single-topic-client-unprotected.p12 -nodes -password pass:pushy-test

# Generate a PKCS#12 keystore with multiple keys
for i in `seq 1 4`;
do
  # Couldn't find a way to get multiple keys into a PKCS#12 file using OpenSSL directly, so we'll take the long way
  # around and construct a multi-key-pair keystore with keytool, then export to PKCS#12.
  keytool -genkeypair -storepass pushy-test -keypass pushy-test -dname "CN=com.relayrides.pushy.{$i}" -keystore multiple-keys.jks -alias "pair${i}"
done

keytool -importkeystore -srckeystore multiple-keys.jks -destkeystore multiple-keys.p12 -srcstoretype JKS -deststoretype PKCS12 -srcstorepass pushy-test -deststorepass pushy-test

# Generate a PKCS#12 with a certificate, but no private key
openssl pkcs12 -export -in ca.pem -nokeys -out no-keys.p12 -password pass:pushy-test

# Generate a private key for token authentication testing
openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out token-auth-private-key.p8

# Clean up intermediate files
rm ca.key server.key multi-topic-client.key single-topic-client.key
rm multiple-keys.jks

# Generate an elliptic key pair for token authentication testing
openssl ecparam -name prime256v1 -genkey -noout | openssl pkcs8 -topk8 -nocrypt -out token-auth-private-key.p8
openssl ec -in token-auth-private-key.p8 -pubout -out token-auth-public-key.p8
