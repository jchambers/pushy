package com.relayrides.pushy.apns;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;

public class ApnsVerificationKeyRegistryTest extends ApnsKeyRegistryTest<ApnsVerificationKey> {

    @Override
    protected ApnsKeyRegistry<ApnsVerificationKey> getNewRegistry() {
        return new ApnsVerificationKeyRegistry();
    }

    @Override
    protected ApnsVerificationKey getNewKey(String keyId, String teamId) throws NoSuchAlgorithmException {
        return new ApnsVerificationKey(keyId, teamId, (ECPublicKey) KeyPairUtil.generateKeyPair().getPublic());
    }

    // TODO Test getting keys from files/input streams
}
