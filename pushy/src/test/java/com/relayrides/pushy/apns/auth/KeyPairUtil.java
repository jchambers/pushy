package com.relayrides.pushy.apns.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class KeyPairUtil {
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

        keyPairGenerator.initialize(256, random);

        return keyPairGenerator.generateKeyPair();
    }
}
