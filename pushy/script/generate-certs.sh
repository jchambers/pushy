#!/bin/sh

# Generate a new, self-signed root CA
openssl req -extensions v3_ca -new -x509 -days 36500 -nodes -subj "/CN=PushyTestRoot" -newkey rsa:2048 -sha512 -out ca-cert.pem -keyout ca-key.pem

# Generate a certificate/key for the server
openssl req -new -keyout server-key.pem -nodes -newkey rsa:2048 -subj "/CN=com.relayrides.pushy" | \
    openssl x509 -req -CAkey ca-key.pem -CA ca-cert.pem -days 36500 -set_serial $RANDOM -sha512 -out server-cert.pem

# We'll also want a DER version of the key for certain tests
openssl pkcs8 -topk8 -inform PEM -outform DER -in server-key.pem -out server-key.der -nocrypt

# Clean up intermediate files
rm ca-key.pem
