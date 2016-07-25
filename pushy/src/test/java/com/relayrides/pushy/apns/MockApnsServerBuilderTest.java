package com.relayrides.pushy.apns;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;

import org.junit.Test;

public class MockApnsServerBuilderTest {

    private static final String SERVER_CERTIFICATE_FILENAME = "/server.pem";
    private static final String SERVER_KEY_FILENAME = "/server.key";
    private static final String SERVER_KEYSTORE_FILENAME = "/server.p12";
    private static final String KEYSTORE_PASSWORD = "pushy-test";

    @Test
    public void testSetServerCredentialsFileFileString() throws Exception {
        final File certificateFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_CERTIFICATE_FILENAME).toURI());
        final File keyFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_KEY_FILENAME).toURI());

        // We're happy here as long as nothing explodes
        new MockApnsServerBuilder()
        .setServerCredentials(certificateFile, keyFile, null)
        .build();
    }

    @Test
    public void testSetServerCredentialsInputStreamInputStreamString() throws Exception {
        try (final InputStream certificateInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME);
                final InputStream keyInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_KEY_FILENAME)) {

            // We're happy here as long as nothing explodes
            new MockApnsServerBuilder()
            .setServerCredentials(certificateInputStream, keyInputStream, null)
            .build();
        }
    }

    @Test
    public void testSetServerCredentialsX509CertificateArrayPrivateKeyString() throws Exception {
        try (final InputStream keystoreInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry = P12Util.getFirstPrivateKeyEntryFromP12InputStream(keystoreInputStream, KEYSTORE_PASSWORD);

            // We're happy here as long as nothing explodes
            new MockApnsServerBuilder()
            .setServerCredentials(new X509Certificate[] { (X509Certificate) privateKeyEntry.getCertificate() }, privateKeyEntry.getPrivateKey(), null)
            .build();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutServerCredentials() throws Exception {
        new MockApnsServerBuilder().build();
    }
}
