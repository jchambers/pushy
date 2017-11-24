/*
 * Copyright (c) 2013-2017 Turo
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.turo.pushy.apns.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.turo.pushy.apns.util.DateAsTimeSinceEpochTypeAdapter;
import io.netty.util.AsciiString;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>An authentication token (or "provider authentication token" or "provider token" in Apple's terminology) is a
 * JSON Web Token (JWT) that contains cryptographically-signed claims about the identity of the sender that can be used
 * by APNs clients in lieu of mutual TLS authentication to authenticate with an APNs server. Authentication tokens
 * contain "claims" that identify the development team sending push notifications as well as the specific key used to
 * sign the token.</p>
 *
 * <p>When clients use token-based authentication, they send an authentication token with each push notification. Tokens
 * may expire, in which case clients must discard the old token and generate a new one. Callers generally do
 * <em>not</em> need to interact with authentication tokens directly; {@link com.turo.pushy.apns.ApnsClient} instances
 * using token-based authentication will manage authentication tokens automatically.</p>
 *
 * <p>Tokens may be constructed from an {@link ApnsSigningKey} (for clients sending notifications) or from a
 * Base64-encoded JWT string (for servers verifying a token from a client).</p>
 *
 * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Local
 * and Remote Notification Programming Guide - Communicating with APNs</a>
 *
 * @see <a href="https://tools.ietf.org/html/rfc7519">RFC 7519 - JSON Web Token (JWT)</a>
 *
 * @see com.turo.pushy.apns.ApnsClientBuilder#setSigningKey(ApnsSigningKey)
 */
public class AuthenticationToken {

    private static class AuthenticationTokenHeader {
        @SerializedName("alg")
        private final String algorithm = "ES256";

        @SerializedName("typ")
        private final String tokenType = "JWT";

        @SerializedName("kid")
        private final String keyId;

        AuthenticationTokenHeader(final String keyId) {
            this.keyId = keyId;
        }

        String getKeyId() {
            return this.keyId;
        }
    }

    private static class AuthenticationTokenClaims {

        @SerializedName("iss")
        private final String issuer;

        @SerializedName("iat")
        private final Date issuedAt;

        AuthenticationTokenClaims(final String teamId, final Date issuedAt) {
            this.issuer = teamId;
            this.issuedAt = issuedAt;
        }

        String getIssuer() {
            return this.issuer;
        }

        Date getIssuedAt() {
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

    private transient final String base64EncodedToken;
    private transient final AsciiString authorizationHeader;

    /**
     * Constructs a new authentication token using the given signing key (and associated metadata) issued at the given
     * date.
     *
     * @param signingKey the signing key from which to derive metadata and with which to sign the token
     * @param issuedAt the time at which the token was issued
     *
     * @throws NoSuchAlgorithmException if the JVM doesn't support the
     * {@value com.turo.pushy.apns.auth.ApnsKey#APNS_SIGNATURE_ALGORITHM} algorithm
     * @throws InvalidKeyException if the given key was invalid for any reason
     * @throws SignatureException if the given key could not be used to sign the token
     */
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
        this.authorizationHeader = new AsciiString("bearer " + payloadBuilder.toString());
    }

    /**
     * Constructs a new authentication token from a Base64-encoded JWT string. Note that successfully creating a token
     * from an encoded string does <em>not</em> imply that the token is valid.
     *
     * @param base64EncodedToken a Base64-encoded JWT string
     */
    public AuthenticationToken(final String base64EncodedToken) {
        Objects.requireNonNull(base64EncodedToken, "Encoded token must not be null.");

        this.base64EncodedToken = base64EncodedToken;
        this.authorizationHeader = new AsciiString("bearer " + base64EncodedToken);

        final String[] jwtSegments = base64EncodedToken.split("\\.");

        if (jwtSegments.length != 3) {
            throw new IllegalArgumentException();
        }

        this.header = GSON.fromJson(new String(Base64.decodeBase64(jwtSegments[0]), StandardCharsets.US_ASCII), AuthenticationTokenHeader.class);
        this.claims = GSON.fromJson(new String(Base64.decodeBase64(jwtSegments[1]), StandardCharsets.US_ASCII), AuthenticationTokenClaims.class);
        this.signatureBytes = Base64.decodeBase64(jwtSegments[2]);
    }

    /**
     * Returns the time at which this token was issued.
     *
     * @return the time at which this token was issued
     */
    public Date getIssuedAt() {
        return this.claims.getIssuedAt();
    }

    /**
     * Returns the Apple-issued ID of the key used to sign this token.
     *
     * @return the Apple-issued ID of the key used to sign this token
     */
    public String getKeyId() {
        return this.header.getKeyId();
    }

    /**
     * Returns the Apple-issued ID of the team to which this authentication token's key pair belongs.
     *
     * @return the Apple-issued ID of the team to which this authentication token's key pair belongs
     */
    public String getTeamId() {
        return this.claims.getIssuer();
    }

    /**
     * Verifies the cryptographic signature of this authentication token.
     *
     * @param verificationKey the verification key (public key) to be used to verify this token's signature
     *
     * @return {@code true} if this token's signature was verified or {@code false} otherwise
     *
     * @throws NoSuchAlgorithmException if the JVM doesn't support the
     * {@value com.turo.pushy.apns.auth.ApnsKey#APNS_SIGNATURE_ALGORITHM} algorithm
     * @throws InvalidKeyException if the given key was invalid for any reason
     * @throws SignatureException if the given key could not be used to verify the token's signature
     */
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

    /**
     * Returns a complete APNs authorization header value (i.e. "bearer [token]") for this authentication token.
     *
     * @return a complete APNs authorization header value for this authentication token
     */
    public AsciiString getAuthorizationHeader() {
        return authorizationHeader;
    }

    /**
     * Returns a Base64-encoded JWT representation of this authentication token.
     *
     * @return a Base64-encoded JWT representation of this authentication token
     */
    @Override
    public String toString() {
        return this.base64EncodedToken;
    }

    @Override
    public int hashCode() {
        return this.base64EncodedToken.hashCode();
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
