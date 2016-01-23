package com.relayrides.pushy.apns;

import java.io.File;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.KeyStore.PrivateKeyEntry;

public class P12Util {

    public static PrivateKeyEntry getPrivateKeyEntryFromP12File(final File p12File, final String password) throws KeyStoreException, NoSuchAlgorithmException, UnrecoverableEntryException {
        final KeyStore.PasswordProtection keyStorePassword = new KeyStore.PasswordProtection(password != null ? password.toCharArray() : null);
        final KeyStore keyStore = KeyStore.Builder.newInstance("PKCS12", null, p12File, keyStorePassword).getKeyStore();

        if (keyStore.size() != 1) {
            throw new KeyStoreException("Key store must contain exactly one entry, and that entry must be a private key entry.");
        }

        final String alias = keyStore.aliases().nextElement();
        final KeyStore.Entry entry = keyStore.getEntry(alias, keyStorePassword);

        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            throw new KeyStoreException("Key store must contain exactly one entry, and that entry must be a private key entry.");
        }

        return (PrivateKeyEntry) entry;
    }
}
