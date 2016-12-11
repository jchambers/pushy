package com.relayrides.pushy.apns;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

class KeyPairUtil {
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

        keyPairGenerator.initialize(256, random);

        final KeyPair keyPair = keyPairGenerator.generateKeyPair();
        return keyPair;
    }
}
