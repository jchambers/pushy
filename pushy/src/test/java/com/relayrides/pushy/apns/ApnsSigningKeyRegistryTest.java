package com.relayrides.pushy.apns;

import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;

public class ApnsSigningKeyRegistryTest extends ApnsKeyRegistryTest<ApnsSigningKey> {

    @Override
    protected ApnsKeyRegistry<ApnsSigningKey> getNewRegistry() {
        return new ApnsSigningKeyRegistry();
    }

    @Override
    protected ApnsSigningKey getNewKey(final String keyId, final String teamId) throws NoSuchAlgorithmException {
        return new ApnsSigningKey(keyId, teamId, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());
    }
}
