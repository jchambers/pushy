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

import com.turo.pushy.apns.auth.ApnsSigningKey;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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

    private static NioEventLoopGroup EVENT_LOOP_GROUP;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        EVENT_LOOP_GROUP = new NioEventLoopGroup(1);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        EVENT_LOOP_GROUP.shutdownGracefully().await();
    }

    @Test
    public void testBuildClientWithPasswordProtectedP12File() throws Exception {
        // We're happy here as long as nothing throws an exception
        new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), KEYSTORE_PASSWORD)
                .build();
    }

    @Test
    public void testBuildClientWithPasswordProtectedP12InputStream() throws Exception {
        // We're happy here as long as nothing throws an exception
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME)) {
            new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                    .build();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testBuildClientWithNullPassword() throws Exception {
        new ApnsClientBuilder()
                .setEventLoopGroup(EVENT_LOOP_GROUP)
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
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
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
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), null)
                    .build();
        }
    }

    @Test
    public void testBuildWithSigningKey() throws Exception {
        try (final InputStream p8InputStream = this.getClass().getResourceAsStream(SIGNING_KEY_FILENAME)) {
            final ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(p8InputStream, "TEAM_ID", "KEY_ID");

            // We're happy here as long as nothing explodes
            new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setEventLoopGroup(EVENT_LOOP_GROUP)
                    .setSigningKey(signingKey)
                    .build();
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutClientCredentials() throws Exception {
        new ApnsClientBuilder()
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithClientCredentialsAndSigningCertificate() throws Exception {
        try (final InputStream p12InputStream = this.getClass().getResourceAsStream(SINGLE_TOPIC_CLIENT_KEYSTORE_UNPROTECTED_FILENAME)) {

            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            try (final InputStream p8InputStream = this.getClass().getResourceAsStream(SIGNING_KEY_FILENAME)) {

                final ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(p8InputStream, "TEAM_ID", "KEY_ID");

                new ApnsClientBuilder()
                        .setEventLoopGroup(EVENT_LOOP_GROUP)
                        .setClientCredentials((X509Certificate) privateKeyEntry.getCertificate(), privateKeyEntry.getPrivateKey(), null)
                        .setSigningKey(signingKey)
                        .build();
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutApnsServerAddress() throws Exception {
        new ApnsClientBuilder()
                .setEventLoopGroup(EVENT_LOOP_GROUP)
                .setClientCredentials(new File(this.getClass().getResource(SINGLE_TOPIC_CLIENT_KEYSTORE_FILENAME).toURI()), KEYSTORE_PASSWORD)
                .build();
    }
}
