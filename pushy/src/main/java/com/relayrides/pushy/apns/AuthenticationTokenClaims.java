package com.relayrides.pushy.apns;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

/**
 * A model object representing the "claims" section of a JWT payload.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href="https://tools.ietf.org/html/rfc7519#section-4">RFC 7519: JSON Web Token (JWT), Section 4: JWT Claims</a>
 *
 * @since 0.9
 */
class AuthenticationTokenClaims {

    @SerializedName("iss")
    private final String issuer;

    @SerializedName("iat")
    private final Date issuedAt;

    public AuthenticationTokenClaims(final String issuer, final Date issuedAt) {
        this.issuer = issuer;
        this.issuedAt = issuedAt;
    }

    public String getIssuer() {
        return this.issuer;
    }

    public Date getIssuedAt() {
        return this.issuedAt;
    }
}