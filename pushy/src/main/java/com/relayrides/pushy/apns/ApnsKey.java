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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.key == null) ? 0 : this.key.hashCode());
        result = prime * result + ((this.keyId == null) ? 0 : this.keyId.hashCode());
        result = prime * result + ((this.teamId == null) ? 0 : this.teamId.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ApnsKey)) {
            return false;
        }
        final ApnsKey other = (ApnsKey) obj;
        if (this.key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!this.key.equals(other.key)) {
            return false;
        }
        if (this.keyId == null) {
            if (other.keyId != null) {
                return false;
            }
        } else if (!this.keyId.equals(other.keyId)) {
            return false;
        }
        if (this.teamId == null) {
            if (other.teamId != null) {
                return false;
            }
        } else if (!this.teamId.equals(other.teamId)) {
            return false;
        }
        return true;
    }
}
