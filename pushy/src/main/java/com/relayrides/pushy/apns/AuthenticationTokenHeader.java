package com.relayrides.pushy.apns;

import com.google.gson.annotations.SerializedName;

class AuthenticationTokenHeader {
    public static final String ALGORITHM = "ES256";
    public static final String TOKEN_TYPE = "JWT";

    @SerializedName("alg")
    private final String algorithm = ALGORITHM;

    @SerializedName("typ")
    private final String tokenType = TOKEN_TYPE;

    @SerializedName("kid")
    private final String keyId;

    public AuthenticationTokenHeader(final String keyId) {
        this.keyId = keyId;
    }

    public String getAlgorithm() {
        return this.algorithm;
    }

    public String getTokenType() {
        return this.tokenType;
    }

    public String getKeyId() {
        return this.keyId;
    }
}
