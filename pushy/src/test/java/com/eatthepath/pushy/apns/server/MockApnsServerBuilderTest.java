/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns.server;

import com.eatthepath.ApnsTestCertificates;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MockApnsServerBuilderTest {

    private static X509Bundle CERTIFICATE_BUNDLE;

    @BeforeAll
    static void setUpBeforeAll() throws Exception {
        CERTIFICATE_BUNDLE = new ApnsTestCertificates().getTrustedServerCertificateBundle();
    }

    @Test
    void testSetServerCredentialsFileFileString() throws Exception {
        final File certificateFile = File.createTempFile("pushy-test", ".pem");
        certificateFile.deleteOnExit();

        try (final OutputStream certificateOutputStream = Files.newOutputStream(certificateFile.toPath())) {
            certificateOutputStream.write(CERTIFICATE_BUNDLE.getCertificatePathPEM().getBytes(StandardCharsets.UTF_8));
        }

        final File keyFile = File.createTempFile("pushy-test", ".p8");
        keyFile.deleteOnExit();

        try (final OutputStream keyOutputStream = Files.newOutputStream(keyFile.toPath())) {
            keyOutputStream.write(CERTIFICATE_BUNDLE.getPrivateKeyPEM().getBytes(StandardCharsets.UTF_8));
        }

        assertDoesNotThrow(() -> new MockApnsServerBuilder()
            .setServerCredentials(certificateFile, keyFile, null)
            .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
            .build());
    }

    @Test
    void testSetServerCredentialsInputStreamInputStreamString() throws Exception {

        try (final InputStream certificateInputStream =
                 new ByteArrayInputStream(CERTIFICATE_BUNDLE.getCertificatePathPEM().getBytes(StandardCharsets.UTF_8));
             final InputStream keyInputStream =
                 new ByteArrayInputStream(CERTIFICATE_BUNDLE.getPrivateKeyPEM().getBytes(StandardCharsets.UTF_8))) {

            assertDoesNotThrow(() -> new MockApnsServerBuilder()
                .setServerCredentials(certificateInputStream, keyInputStream, null)
                .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
                .build());
        }
    }

    @Test
    void testSetServerCredentialsX509CertificateArrayPrivateKeyString() {
        assertDoesNotThrow(() -> new MockApnsServerBuilder()
            .setServerCredentials(CERTIFICATE_BUNDLE.getCertificatePathWithRoot(),
                CERTIFICATE_BUNDLE.getKeyPair().getPrivate())
            .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
            .build());
    }

    @Test
    void testBuildWithoutServerCredentials() {
        assertThrows(IllegalStateException.class, () -> new MockApnsServerBuilder()
                .setHandlerFactory(new AcceptAllPushNotificationHandlerFactory())
                .build());
    }

    @Test
    void testBuildWithoutHandlerFactory() {
        assertThrows(IllegalStateException.class, () -> new MockApnsServerBuilder()
            .setServerCredentials(CERTIFICATE_BUNDLE.getCertificatePathWithRoot(),
                CERTIFICATE_BUNDLE.getKeyPair().getPrivate())
            .build());
    }

    @Test
    void testSetMaxConcurrentStreams() {
        assertDoesNotThrow(() -> new MockApnsServerBuilder().setMaxConcurrentStreams(1));
    }

    @Test
    void testSetNegativeMaxConcurrentStreams() {
        assertThrows(IllegalArgumentException.class, () -> new MockApnsServerBuilder().setMaxConcurrentStreams(-7));
    }
}
