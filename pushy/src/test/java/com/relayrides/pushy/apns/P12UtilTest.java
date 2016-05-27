package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.KeyStore.PrivateKeyEntry;

import org.junit.Test;

public class P12UtilTest {

    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME = "/single-topic-client.p12";
    private static final String MULTIPLE_KEY_KEYSTORE_FILENAME = "/multiple-keys.p12";

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    @Test
    public void testGetPrivateKeyEntryFromP12InputStream() throws Exception {
        try (final InputStream p12InputStream = P12UtilTest.class.getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            assertNotNull(privateKeyEntry);
        }
    }

    @Test(expected = KeyStoreException.class)
    public void testGetPrivateKeyEntryFromP12InputStreamWithMultipleKeys() throws Exception {
        try (final InputStream p12InputStream = P12UtilTest.class.getResourceAsStream(MULTIPLE_KEY_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            assertNotNull(privateKeyEntry);
        }
    }
}
