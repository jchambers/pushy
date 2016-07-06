package com.relayrides.pushy.apns;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

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
        return issuer;
    }

    public Date getIssuedAt() {
        return issuedAt;
    }
}
