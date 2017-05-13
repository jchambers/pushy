/*
 * Copyright (c) 2013-2017 Turo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.turo.pushy.apns;

import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;

public class MockApnsServerBuilderTest {

    private static final String SERVER_CERTIFICATE_FILENAME = "/server-certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server-key.pem";
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
