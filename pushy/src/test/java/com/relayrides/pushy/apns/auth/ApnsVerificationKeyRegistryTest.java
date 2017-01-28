package com.relayrides.pushy.apns.auth;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;

import com.relayrides.pushy.apns.auth.ApnsKeyRegistry;
import com.relayrides.pushy.apns.auth.ApnsVerificationKey;
import com.relayrides.pushy.apns.auth.ApnsVerificationKeyRegistry;

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
