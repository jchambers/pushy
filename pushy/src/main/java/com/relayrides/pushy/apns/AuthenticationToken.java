package com.relayrides.pushy.apns;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.relayrides.pushy.apns.auth.ApnsKey;
import com.relayrides.pushy.apns.auth.ApnsSigningKey;
import com.relayrides.pushy.apns.auth.ApnsVerificationKey;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class AuthenticationToken {

    private static class AuthenticationTokenHeader {
        @SerializedName("alg")
        private final String algorithm = "ES256";

        @SerializedName("typ")
        private final String tokenType = "JWT";

        @SerializedName("kid")
        private final String keyId;

        public AuthenticationTokenHeader(final String keyId) {
            this.keyId = keyId;
        }

        @SuppressWarnings("unused")
        public String getAlgorithm() {
            return this.algorithm;
        }

        @SuppressWarnings("unused")
        public String getTokenType() {
            return this.tokenType;
        }

        public String getKeyId() {
            return this.keyId;
        }
    }

    private static class AuthenticationTokenClaims {

        @SerializedName("iss")
        private final String issuer;

        @SerializedName("iat")
        private final Date issuedAt;

        public AuthenticationTokenClaims(final String teamId, final Date issuedAt) {
            this.issuer = teamId;
            this.issuedAt = issuedAt;
        }

        public String getIssuer() {
            return this.issuer;
        }

        public Date getIssuedAt() {
            return this.issuedAt;
        }
    }

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS))
            .create();

    private final AuthenticationTokenHeader header;
    private final AuthenticationTokenClaims claims;
    private final byte[] signatureBytes;

    private final String base64EncodedToken;

    public AuthenticationToken(final ApnsSigningKey signingKey, final Date issuedAt) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        this.header = new AuthenticationTokenHeader(signingKey.getKeyId());
        this.claims = new AuthenticationTokenClaims(signingKey.getTeamId(), issuedAt);

        final String headerJson = GSON.toJson(this.header);
        final String claimsJson = GSON.toJson(this.claims);

        final StringBuilder payloadBuilder = new StringBuilder();
        payloadBuilder.append(Base64.encodeBase64URLSafeString(headerJson.getBytes(StandardCharsets.US_ASCII)));
        payloadBuilder.append('.');
        payloadBuilder.append(Base64.encodeBase64URLSafeString(claimsJson.getBytes(StandardCharsets.US_ASCII)));

        {
            final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);
            signature.initSign(signingKey);
            signature.update(payloadBuilder.toString().getBytes(StandardCharsets.US_ASCII));

            this.signatureBytes = signature.sign();
        }

        payloadBuilder.append('.');
        payloadBuilder.append(Base64.encodeBase64URLSafeString(this.signatureBytes));

        this.base64EncodedToken = payloadBuilder.toString();
    }

    public AuthenticationToken(final String base64EncodedToken) {
        this.base64EncodedToken = base64EncodedToken;

        final String[] pieces = this.base64EncodedToken.split("\\.");

        if (pieces.length != 3) {
            throw new IllegalArgumentException();
        }

        this.header = GSON.fromJson(new String(Base64.decodeBase64(pieces[0]), StandardCharsets.US_ASCII), AuthenticationTokenHeader.class);
        this.claims = GSON.fromJson(new String(Base64.decodeBase64(pieces[1]), StandardCharsets.US_ASCII), AuthenticationTokenClaims.class);
        this.signatureBytes = Base64.decodeBase64(pieces[2]);
    }

    public Date getIssuedAt() {
        return this.claims.getIssuedAt();
    }

    public String getKeyId() {
        return this.header.getKeyId();
    }

    public String getTeamId() {
        return this.claims.getIssuer();
    }

    public boolean verifySignature(final ApnsVerificationKey verificationKey) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        if (!this.header.getKeyId().equals(verificationKey.getKeyId())) {
            return false;
        }

        if (!this.claims.getIssuer().equals(verificationKey.getTeamId())) {
            return false;
        }

        final byte[] headerAndClaimsBytes;

        final String headerJson = GSON.toJson(this.header);
        final String claimsJson = GSON.toJson(this.claims);

        final StringBuilder headerAndClaimsBuilder = new StringBuilder();

        headerAndClaimsBuilder.append(Base64.encodeBase64URLSafeString(headerJson.getBytes(StandardCharsets.US_ASCII)));
        headerAndClaimsBuilder.append('.');
        headerAndClaimsBuilder.append(Base64.encodeBase64URLSafeString(claimsJson.getBytes(StandardCharsets.US_ASCII)));

        headerAndClaimsBytes = headerAndClaimsBuilder.toString().getBytes(StandardCharsets.US_ASCII);

        final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);
        signature.initVerify(verificationKey);
        signature.update(headerAndClaimsBytes);

        return signature.verify(this.signatureBytes);
    }

    @Override
    public String toString() {
        return this.base64EncodedToken;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.base64EncodedToken == null) ? 0 : this.base64EncodedToken.hashCode());
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
        if (!(obj instanceof AuthenticationToken)) {
            return false;
        }
        final AuthenticationToken other = (AuthenticationToken) obj;
        if (this.base64EncodedToken == null) {
            if (other.base64EncodedToken != null) {
                return false;
            }
        } else if (!this.base64EncodedToken.equals(other.base64EncodedToken)) {
            return false;
        }
        return true;
    }
}
