package com.relayrides.pushy.apns;

import com.relayrides.pushy.apns.auth.ApnsSigningKey;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;

public class ApnsClientBuilderTest {

    private static final String SIGNING_KEY_FILENAME = "/token-auth-private-key.p8";

    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME = "/single-topic-client.p12";
    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME = "/single-topic-client-unprotected.p12";

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    @Test
    public void testBuildClientWithPasswordProtectedP12File() throws Exception {
        // We're happy here as long as nothing throws an exception
        new ApnsClientBuilder()
                .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), KEYSTORE_PASSWORD)
                .build();
    }

    @Test
    public void testBuildClientWithPasswordProtectedP12InputStream() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            new ApnsClientBuilder()
                    .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                    .build();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testBuildClientWithNullPassword() throws Exception {
        new ApnsClientBuilder()
                .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), null)
                .build();
    }

    @Test
    public void testBuildClientWithCertificateAndPasswordProtectedKey() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            new ApnsClientBuilder()
                    .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), KEYSTORE_PASSWORD)
                    .build();
        }
    }

    @Test
    public void testBuildClientWithCertificateAndUnprotectedKey() throws Exception {
        // We DO need a password to unlock the keystore, but the key itself should be unprotected
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME)) {

            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            new ApnsClientBuilder()
                    .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), null)
                    .build();
        }
    }

    @Test
    public void testBuildWithSigningKey() throws Exception {
        try (final InputStream p8InputStream = this.getClass().getResourceAsStream(SIGNING_KEY_FILENAME)) {
            final ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(p8InputStream, "TEAM_ID", "KEY_ID");

            new ApnsClientBuilder()
                    .setSigningKey(signingKey)
                    .build();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutClientCredentials() throws Exception {
        new ApnsClientBuilder().build();
    }
}
