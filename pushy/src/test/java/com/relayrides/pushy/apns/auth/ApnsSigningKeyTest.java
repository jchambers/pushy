package com.relayrides.pushy.apns.auth;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class ApnsSigningKeyTest extends ApnsKeyTest {

    @Override
    protected ApnsKey getApnsKey() throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        return ApnsSigningKey.loadFromInputStream(this.getClass().getResourceAsStream("/token-auth-private-key.p8"), "Team ID", "Key ID");
    }
}
