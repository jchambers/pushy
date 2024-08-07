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

package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ApnsClientBuilderTest {

    private static final String SIGNING_KEY_FILENAME = "/token-auth-private-key.p8";

    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME = "/single-topic-client.p12";
    private static final String SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME = "/single-topic-client-unprotected.p12";

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    private static ApnsClientResources CLIENT_RESOURCES;

    @BeforeAll
    public static void setUpBeforeClass() {
        CLIENT_RESOURCES = new ApnsClientResources(new NioEventLoopGroup(1));
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        CLIENT_RESOURCES.shutdownGracefully().await();
    }

    @Test
    void testBuildClientWithPasswordProtectedP12File() throws Exception {
        // We're happy here as long as nothing throws an exception
        final ApnsClient client = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), KEYSTORE_PASSWORD)
                .build();

        client.close().get();
    }

    @Test
    void testBuildClientWithPasswordProtectedP12InputStream() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            final ApnsClient client = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setApnsClientResources(CLIENT_RESOURCES)
                    .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                    .build();

            client.close().get();
        }
    }

    @Test
    void testBuildClientWithNullPassword() {
        assertThrows(NullPointerException.class, () -> new ApnsClientBuilder()
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), null)
                .build());
    }

    @Test
    void testBuildClientWithCertificateAndPasswordProtectedKey() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            final ApnsClient client = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setApnsClientResources(CLIENT_RESOURCES)
                    .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), KEYSTORE_PASSWORD)
                    .build();

            client.close().get();
        }
    }

    @Test
    void testBuildClientWithCertificateAndUnprotectedKeyNoPassword() throws Exception {
        // We DO need a password to unlock the keystore, but the key itself should be unprotected
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME)) {

            final PrivateKeyEntry privateKeyEntry =
                P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            final ApnsClient client = new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey())
                .build();

            client.close().get();
        }
    }

    @Test
    void testBuildClientWithCertificateAndUnprotectedKey() throws Exception {
        // We DO need a password to unlock the keystore, but the key itself should be unprotected
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME)) {

            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            final ApnsClient client = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setApnsClientResources(CLIENT_RESOURCES)
                    .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), null)
                    .build();

            client.close().get();
        }
    }

    @Test
    void testBuildWithSigningKey() throws Exception {
        try (final InputStream p8InputStream = this.getClass().getResourceAsStream(SIGNING_KEY_FILENAME)) {
            final ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(p8InputStream, "TEAM_ID", "KEY_ID");

            // We're happy here as long as nothing explodes
            final ApnsClient client = new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setApnsClientResources(CLIENT_RESOURCES)
                    .setSigningKey(signingKey)
                    .build();

            client.close().get();
        }
    }

    @Test
    void testBuildWithoutClientCredentials() {
        assertThrows(IllegalStateException.class, () ->
                new ApnsClientBuilder()
                        .setApnsClientResources(CLIENT_RESOURCES)
                        .build());
    }

    @Test
    void testBuildWithClientCredentialsAndSigningCertificate() throws Exception {
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME)) {

            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            try (final InputStream p8InputStream = this.getClass().getResourceAsStream(SIGNING_KEY_FILENAME)) {

                final ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(p8InputStream, "TEAM_ID", "KEY_ID");

                assertThrows(IllegalStateException.class, () ->
                        new ApnsClientBuilder()
                                .setApnsClientResources(CLIENT_RESOURCES)
                                .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), null)
                                .setSigningKey(signingKey)
                                .build());
            }
        }
    }

    @Test
    void testBuildWithoutApnsServerAddress() {
        assertThrows(IllegalStateException.class, () ->
                new ApnsClientBuilder()
                        .setApnsClientResources(CLIENT_RESOURCES)
                        .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), KEYSTORE_PASSWORD)
                        .build());
    }
}
