package com.relayrides.pushy.apns;

import com.google.gson.annotations.SerializedName;

/**
 * A model object representing the header section of a JWT payload.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href="https://tools.ietf.org/html/rfc7519#section-5">RFC 7519: JSON Web Token (JWT), Section 4: JOSE Header</a>
 *
 * @since 0.9
 */
class AuthenticationTokenHeader {
    @SerializedName("alg")
    private final String algorithm = "ES256";

    @SerializedName("typ")
    private final String tokenType = "JWT";

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
