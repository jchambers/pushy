package com.relayrides.pushy.apns;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;

public class MockApnsServerBuilderTest {

    private static final String SERVER_CERTIFICATES_FILENAME = "/server-certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";

    @Test
    public void testSetServerCredentialsFileFileString() throws Exception {
        final File certificateFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_CERTIFICATES_FILENAME).toURI());
        final File keyFile = new File(MockApnsServerBuilderTest.class.getResource(SERVER_KEY_FILENAME).toURI());

        // We're happy here as long as nothing explodes
        new MockApnsServerBuilder()
        .setServerCredentials(certificateFile, keyFile, null)
        .build();
    }

    @Test
    public void testSetServerCredentialsInputStreamInputStreamString() throws Exception {
        try (final InputStream certificateInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME);
                final InputStream keyInputStream = MockApnsServerBuilderTest.class.getResourceAsStream(SERVER_KEY_FILENAME)) {

            // We're happy here as long as nothing explodes
            new MockApnsServerBuilder()
            .setServerCredentials(certificateInputStream, keyInputStream, null)
            .build();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutServerCredentials() throws Exception {
        new MockApnsServerBuilder().build();
    }
}
