package com.relayrides.pushy.apns;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import org.junit.Test;

public class MockApnsServerBuilderTest {

    private static final String SERVER_CERTIFICATE_FILENAME = "/server-cert.pem";
    private static final String SERVER_KEY_PEM_FILENAME = "/server-key.pem";
    private static final String SERVER_KEY_DER_FILENAME = "/server-key.der";

    @Test
    public void testSetServerCredentialsFileFileString() throws Exception {
        final File certificateFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_CERTIFICATE_FILENAME).toURI());
        final File keyFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_KEY_PEM_FILENAME).toURI());

        // We're happy here as long as nothing explodes
        new MockApnsServerBuilder()
        .setServerCredentials(certificateFile, keyFile, null)
        .build();
    }

    @Test
    public void testSetServerCredentialsInputStreamInputStreamString() throws Exception {
        try (final InputStream certificateInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME);
                final InputStream keyInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_KEY_PEM_FILENAME)) {

            // We're happy here as long as nothing explodes
            new MockApnsServerBuilder()
            .setServerCredentials(certificateInputStream, keyInputStream, null)
            .build();
        }
    }

    @Test
    public void testSetServerCredentialsX509CertificateArrayPrivateKeyString() throws Exception {
        final X509Certificate certificate;

        try (final InputStream certificateInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_CERTIFICATE_FILENAME)) {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            certificate = (X509Certificate) certificateFactory.generateCertificate(certificateInputStream);
        }

        final PrivateKey privateKey;

        {
            final File privateKeyFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_KEY_DER_FILENAME).toURI());

            try (final DataInputStream keyInputStream = new DataInputStream(new FileInputStream(privateKeyFile))) {
                final byte[] keyBytes = new byte[(int) privateKeyFile.length()];
                keyInputStream.readFully(keyBytes);

                final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                privateKey = keyFactory.generatePrivate(keySpec);
            }
        }

        // We're happy here as long as nothing explodes
        new MockApnsServerBuilder()
        .setServerCredentials(new X509Certificate[] { certificate }, privateKey, null)
        .build();

    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutServerCredentials() throws Exception {
        new MockApnsServerBuilder().build();
    }
}
