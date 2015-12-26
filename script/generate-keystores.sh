#!/bin/sh

# Generate new keys and certificates for the client and server
keytool -genkeypair -keysize 2048 -validity 36500 -keyalg RSA \
    -dname "CN=com.relayrides.pushy" \
    -keystore pushy-test-server.jks -alias pushy-test-server -keypass pushy-test -storepass pushy-test

keytool -genkeypair  -keysize 2048 -validity 36500 -keyalg RSA \
    -dname "CN=Apple Push Services: com.relayrides.pushy, UID=com.relayrides.pushy" \
    -keystore pushy-test-client-single-topic.jks -alias pushy-test-client-single-topic -keypass pushy-test -storepass pushy-test

# The extension value here is a hex representation of the DER-encoded ASN.1 values from `generate-topic-list-extension.py`
keytool -genkeypair  -keysize 2048 -validity 36500 -keyalg RSA \
    -dname "CN=Apple Push Services: com.relayrides.pushy, UID=com.relayrides.pushy" \
    -ext 1.2.840.113635.100.6.3.6=30730c14636f6d2e72656c617972696465732e707573687930050c036170700c19636f6d2e72656c617972696465732e70757368792e766f697030060c04766f69700c21636f6d2e72656c617972696465732e70757368792e636f6d706c69636174696f6e300e0c0c636f6d706c69636174696f6e \
    -keystore pushy-test-client-multi-topic.jks -alias pushy-test-client-multi-topic -keypass pushy-test -storepass pushy-test

# Import the server certificate into the client trust stores
keytool -export -keystore pushy-test-server.jks -alias pushy-test-server -storepass pushy-test -file pushy-test-server.crt

keytool -import -noprompt -file pushy-test-server.crt -keystore pushy-test-client-single-topic.jks -alias pushy-test-server -storepass pushy-test
keytool -import -noprompt -file pushy-test-server.crt -keystore pushy-test-client-multi-topic.jks -alias pushy-test-server -storepass pushy-test

rm pushy-test-server.crt

# Import the client certificates into the server trust store
keytool -export -keystore pushy-test-client-single-topic.jks -alias pushy-test-client-single-topic \
    -storepass pushy-test -file pushy-test-client-single-topic.crt

keytool -export -keystore pushy-test-client-multi-topic.jks -alias pushy-test-client-multi-topic \
    -storepass pushy-test -file pushy-test-client-multi-topic.crt

keytool -import -noprompt -file pushy-test-client-single-topic.crt \
    -keystore pushy-test-server.jks -alias pushy-test-client-single-topic -storepass pushy-test

keytool -import -noprompt -file pushy-test-client-multi-topic.crt \
    -keystore pushy-test-server.jks -alias pushy-test-client-multi-topic -storepass pushy-test

rm pushy-test-client-single-topic.crt
rm pushy-test-client-multi-topic.crt

# Generate a client key not trusted by the server (by virtue of not being imported into the server's trust store above)
keytool -genkeypair  -keysize 2048 -validity 36500 -keyalg RSA \
    -dname "CN=Apple Push Services: com.relayrides.pushy, UID=com.relayrides.pushy" \
    -keystore pushy-test-client-untrusted.jks -alias pushy-test-client-untrusted -keypass pushy-test -storepass pushy-test
