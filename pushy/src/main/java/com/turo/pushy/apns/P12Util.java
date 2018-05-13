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

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting private keys from P12 files.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class P12Util {

    private static final Pattern COMMON_NAME_PATTERN = Pattern.compile("CN=(.*?):");

    private static final List<String> APNS_SUBJECTS = Arrays.asList(
                    "Apple Push Services",
                    "Apple Production IOS Push Services",
                    "Apple Development IOS Push Services");

    /**
     * Returns the first private key entry found in the given keystore that appears to be a valid certificate/key pair
     * for use as APNs client credentials. If more than one eligible private key entry is present, which key will be
     * returned is undefined.
     *
     * @param p12InputStream an input stream for a PKCS#12 keystore
     * @param password the password to be used to load the keystore and its entries; may be blank, but must not be
     * {@code null}
     *
     * @return the first private key entry found in the given keystore
     *
     * @throws KeyStoreException if a private key entry could not be extracted from the given keystore for any reason
     * @throws IOException if the given input stream could not be read for any reason
     */
    static PrivateKeyEntry getFirstApnsPrivateKeyEntry(final InputStream p12InputStream, final String password) throws KeyStoreException, IOException {
        Objects.requireNonNull(password, "Password may be blank, but must not be null.");

        final KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try {
            keyStore.load(p12InputStream, password.toCharArray());
        } catch (final NoSuchAlgorithmException | CertificateException e) {
            throw new KeyStoreException(e);
        }

        final Enumeration<String> aliases = keyStore.aliases();
        final KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection(password.toCharArray());

        while (aliases.hasMoreElements()) {
            final String alias = aliases.nextElement();

            KeyStore.Entry entry;

            try {
                try {
                    entry = keyStore.getEntry(alias, passwordProtection);
                } catch (final UnsupportedOperationException e) {
                    entry = keyStore.getEntry(alias, null);
                }
            } catch (final UnrecoverableEntryException | NoSuchAlgorithmException e) {
                throw new KeyStoreException(e);
            }

            if (entry instanceof KeyStore.PrivateKeyEntry) {
                final PrivateKeyEntry privateKeyEntry = (PrivateKeyEntry) entry;
                final X509Certificate certificate = (X509Certificate) privateKeyEntry.getCertificate();

                final Matcher matcher = COMMON_NAME_PATTERN.matcher(certificate.getSubjectX500Principal().getName());

                while (matcher.find()) {
                    if (APNS_SUBJECTS.contains(matcher.group(1))) {
                        return privateKeyEntry;
                    }
                }
            }
        }

        throw new KeyStoreException("Key store did not contain any private key entries usable as APNs client credentials.");
    }
}
