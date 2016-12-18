package com.relayrides.pushy.apns;

import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;

class ApnsVerificationKey extends ApnsKey implements ECPublicKey {

    private static final long serialVersionUID = 1L;

    public ApnsVerificationKey(final String keyId, final String teamId, final ECPublicKey key) {
        super(keyId, teamId, key);
    }

    @Override
    public ECPublicKey getKey() {
        return (ECPublicKey) super.getKey();
    }

    @Override
    public String getAlgorithm() {
        return this.getKey().getAlgorithm();
    }

    @Override
    public String getFormat() {
        return this.getKey().getFormat();
    }

    @Override
    public byte[] getEncoded() {
        return this.getKey().getEncoded();
    }

    @Override
    public ECPoint getW() {
        return this.getKey().getW();
    }

}
