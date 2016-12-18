package com.relayrides.pushy.apns;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;

class ApnsSigningKey extends ApnsKey implements ECPrivateKey {

    private static final long serialVersionUID = 1L;

    public ApnsSigningKey(final String keyId, final String teamId, final ECPrivateKey key) {
        super(keyId, teamId, key);
    }

    @Override
    protected ECPrivateKey getKey() {
        return (ECPrivateKey) super.getKey();
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
    public BigInteger getS() {
        return this.getKey().getS();
    }
}
