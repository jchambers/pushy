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

class P12Util {

    public static PrivateKeyEntry getPrivateKeyEntryFromP12InputStream(final InputStream p12InputStream, final String password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException, CertificateException, IOException {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");

        keyStore.load(p12InputStream, password != null ? password.toCharArray() : null);

        final Enumeration<String> aliases = keyStore.aliases();
        KeyStore.PrivateKeyEntry privateKeyEntry = null;

        final KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection(password.toCharArray());

        while (aliases.hasMoreElements()) {
            final String alias = aliases.nextElement();

            KeyStore.Entry entry;

            try {
                entry = keyStore.getEntry(alias, passwordProtection);
            } catch (final UnsupportedOperationException e) {
                entry = keyStore.getEntry(alias, null);
            }

            if (entry instanceof KeyStore.PrivateKeyEntry) {
                if (privateKeyEntry != null) {
                    throw new KeyStoreException("Key store must contain exactly one private key entry.");
                }

                privateKeyEntry = (PrivateKeyEntry) entry;
            }
        }

        if (privateKeyEntry == null) {
            throw new KeyStoreException("Key store must contain exactly one private key entry.");
        }

        return privateKeyEntry;
    }
}
