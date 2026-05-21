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

import com.eatthepath.ApnsTestCertificates;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.security.*;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ApnsClientBuilderTest {

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    private static ApnsTestCertificates TEST_CERTIFICATES;
    private static ApnsClientResources CLIENT_RESOURCES;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        TEST_CERTIFICATES = new ApnsTestCertificates();
        CLIENT_RESOURCES = new ApnsClientResources(new NioEventLoopGroup(1));
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
        CLIENT_RESOURCES.shutdownGracefully().await();
    }

    @Test
    void testBuildClientWithPasswordProtectedP12File() throws Exception {
        final File keystoreFile = File.createTempFile("pushy-test", ".p12");
        keystoreFile.deleteOnExit();

        try (final OutputStream keystoreOutputStream = Files.newOutputStream(keystoreFile.toPath())) {
            TEST_CERTIFICATES.getSingleTopicClientCertificateBundle().toKeyStore(KEYSTORE_PASSWORD.toCharArray())
                .store(keystoreOutputStream, KEYSTORE_PASSWORD.toCharArray());
        }

        final ApnsClient client = assertDoesNotThrow(() -> new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials(keystoreFile, KEYSTORE_PASSWORD)
                .build());

        client.close().get();
    }

    @Test
    void testBuildClientWithPasswordProtectedP12InputStream() throws Exception {
        final File keystoreFile = File.createTempFile("pushy-test", ".p12");
        keystoreFile.deleteOnExit();

        try (final OutputStream keystoreOutputStream = Files.newOutputStream(keystoreFile.toPath())) {
            TEST_CERTIFICATES.getSingleTopicClientCertificateBundle().toKeyStore(KEYSTORE_PASSWORD.toCharArray())
                .store(keystoreOutputStream, KEYSTORE_PASSWORD.toCharArray());
        }

        try (final InputStream p12InputStream = Files.newInputStream(keystoreFile.toPath())) {
            final ApnsClient client = assertDoesNotThrow(() -> new ApnsClientBuilder()
                    .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                    .setApnsClientResources(CLIENT_RESOURCES)
                    .setClientCredentials(p12InputStream, KEYSTORE_PASSWORD)
                    .build());

            client.close().get();
        }
    }

    @Test
    void testBuildClientWithNullPassword() throws Exception {
        final File keystoreFile = File.createTempFile("pushy-test", ".p12");
        keystoreFile.deleteOnExit();

        try (final OutputStream keystoreOutputStream = Files.newOutputStream(keystoreFile.toPath())) {
            TEST_CERTIFICATES.getSingleTopicClientCertificateBundle().toKeyStore(KEYSTORE_PASSWORD.toCharArray())
                .store(keystoreOutputStream, KEYSTORE_PASSWORD.toCharArray());
        }

        assertThrows(NullPointerException.class, () -> new ApnsClientBuilder()
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials(keystoreFile, null)
                .build());
    }

    @Test
    void testBuildClientWithCertificateAndUnprotectedKeyNoPassword() throws Exception {
        final X509Bundle clientCertificates = TEST_CERTIFICATES.getSingleTopicClientCertificateBundle();

        final ApnsClient client = assertDoesNotThrow(() -> new ApnsClientBuilder()
            .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
            .setApnsClientResources(CLIENT_RESOURCES)
            .setClientCredentials(clientCertificates.getCertificate(), clientCertificates.getKeyPair().getPrivate())
            .build());

        client.close().get();
    }

    @Test
    void testBuildClientWithCertificateAndUnprotectedKey() throws Exception {
        final X509Bundle clientCertificates = TEST_CERTIFICATES.getSingleTopicClientCertificateBundle();

        final ApnsClient client = assertDoesNotThrow(() -> new ApnsClientBuilder()
            .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
            .setApnsClientResources(CLIENT_RESOURCES)
            .setClientCredentials(clientCertificates.getCertificate(), clientCertificates.getKeyPair().getPrivate(), null)
            .build());

        client.close().get();
    }

    @Test
    void testBuildWithSigningKey() throws Exception {
        final ApnsClient client = assertDoesNotThrow(() -> new ApnsClientBuilder()
                .setApnsServer(ApnsClientBuilder.PRODUCTION_APNS_HOST)
                .setApnsClientResources(CLIENT_RESOURCES)
                .setSigningKey(generateSigningKey())
                .build());

        client.close().get();
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
        final X509Bundle clientCertificates = TEST_CERTIFICATES.getSingleTopicClientCertificateBundle();

        assertThrows(IllegalStateException.class, () ->
            new ApnsClientBuilder()
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials(clientCertificates.getCertificate(), clientCertificates.getKeyPair().getPrivate())
                .setSigningKey(generateSigningKey())
                .build());
    }

    @Test
    void testBuildWithoutApnsServerAddress() {
        final X509Bundle clientCertificates = TEST_CERTIFICATES.getSingleTopicClientCertificateBundle();

        assertThrows(IllegalStateException.class, () ->
            new ApnsClientBuilder()
                .setApnsClientResources(CLIENT_RESOURCES)
                .setClientCredentials(clientCertificates.getCertificate(), clientCertificates.getKeyPair().getPrivate())
                .build());
    }

    private static ApnsSigningKey generateSigningKey() throws NoSuchAlgorithmException, InvalidKeyException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final KeyPair keyPair = keyPairGenerator.generateKeyPair();

        return new ApnsSigningKey("KEY_ID", "TEAM_ID", (ECPrivateKey) keyPair.getPrivate());
    }
}
