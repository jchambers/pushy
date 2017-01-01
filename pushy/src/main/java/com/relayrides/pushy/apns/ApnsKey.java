package com.relayrides.pushy.apns;

import java.security.interfaces.ECKey;
import java.security.spec.ECParameterSpec;

public abstract class ApnsKey implements ECKey {

    private final String keyId;
    private final String teamId;

    private final ECKey key;

    public ApnsKey(final String keyId, final String teamId, final ECKey key) {
        this.keyId = keyId;
        this.teamId = teamId;

        this.key = key;
    }

    public String getKeyId() {
        return this.keyId;
    }

    public String getTeamId() {
        return this.teamId;
    }

    protected ECKey getKey() {
        return this.key;
    }

    @Override
    public ECParameterSpec getParams() {
        return this.key.getParams();
    }
}
