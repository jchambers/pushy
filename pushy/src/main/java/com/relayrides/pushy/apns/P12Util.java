package com.relayrides.pushy.apns;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Enumeration;
import java.util.Objects;

/**
 * Utility class for extracting private keys from P12 files.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class P12Util {

    /**
     * Returns the first private key entry found in the given keystore. If more than one private key is present, the
     * key that is returned is undefined.
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
    public static PrivateKeyEntry getFirstPrivateKeyEntryFromP12InputStream(final InputStream p12InputStream, final String password) throws KeyStoreException, IOException {
        Objects.requireNonNull(password, "Password may be blank, but must not be null.");

        final KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try {
            keyStore.load(p12InputStream, password.toCharArray());
        } catch (NoSuchAlgorithmException | CertificateException e) {
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
                return (PrivateKeyEntry) entry;
            }
        }

        throw new KeyStoreException("Key store did not contain any private key entries.");
    }
}
