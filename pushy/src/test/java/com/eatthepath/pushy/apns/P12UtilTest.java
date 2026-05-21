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

import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class P12UtilTest {

    private static final String KEYSTORE_PASSWORD = "pushy-test";

    @Test
    void testGetPrivateKeyEntryFromP12InputStream() throws Exception {
        final File keyStoreFile = writeTemporaryKeyStore(1);

        try (final InputStream p12InputStream = Files.newInputStream(keyStoreFile.toPath())) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            assertNotNull(privateKeyEntry);
        }
    }

    @Test
    void testGetPrivateKeyEntryFromP12InputStreamWithMultipleKeys() throws Exception {
        final File keyStoreFile = writeTemporaryKeyStore(4);

        try (final InputStream p12InputStream = Files.newInputStream(keyStoreFile.toPath())) {
            final PrivateKeyEntry privateKeyEntry =
                    P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD);

            assertNotNull(privateKeyEntry);
        }
    }

    @Test
    void testGetPrivateKeyEntryFromP12InputStreamWithNoKeys() throws Exception {
        final File keyStoreFile = writeTemporaryKeyStore(0);

        try (final InputStream p12InputStream = Files.newInputStream(keyStoreFile.toPath())) {
            assertThrows(KeyStoreException.class,
                    () -> P12Util.getFirstPrivateKeyEntryFromP12InputStream(p12InputStream, KEYSTORE_PASSWORD));
        }
    }

    private static File writeTemporaryKeyStore(final int keyCount) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        final KeyStore keyStore;

        try {
            keyStore = KeyStore.getInstance("PKCS12");
        } catch (final KeyStoreException e) {
            throw new AssertionError("Every implementation of the Java platform is required to support PKCS12");
        }

        // Keystores must be initialized before we can add any entries
        keyStore.load(null);

        for (int i = 0; i < keyCount; i++) {
            final String alias = "test-key-" + i;

          final X509Bundle x509Bundle;

          try {
            x509Bundle = new CertificateBuilder()
                .notBefore(Instant.now())
                .notAfter(Instant.now().plus(Duration.ofHours(1)))
                .subject("CN=" + alias)
                .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature, CertificateBuilder.KeyUsage.keyCertSign)
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
          } catch (final Exception e) {
            throw new AssertionError("Failed to build in-memory, self-signed certificate", e);
          }

          keyStore.setKeyEntry(alias, x509Bundle.getKeyPair().getPrivate(), KEYSTORE_PASSWORD.toCharArray(), x509Bundle.getCertificatePath());
        }

        final File keystoreFile = File.createTempFile("pushy-test", ".p12");
        keystoreFile.deleteOnExit();

        try (final OutputStream outputStream = Files.newOutputStream(keystoreFile.toPath())) {
            keyStore.store(outputStream, KEYSTORE_PASSWORD.toCharArray());
        }

        return keystoreFile;
    }
}
