package com.turo.pushy.apns.auth;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class ApnsVerificationKeyTest extends ApnsKeyTest {

    @Override
    protected ApnsKey getApnsKey() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        return ApnsVerificationKey.loadFromInputStream(this.getClass().getResourceAsStream("/token-auth-public-key.p8"), "Team ID", "Key ID");
    }
}
