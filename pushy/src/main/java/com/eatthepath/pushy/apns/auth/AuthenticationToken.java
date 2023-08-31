/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns.auth;

import com.eatthepath.json.JsonParser;
import com.eatthepath.json.JsonSerializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.base64.Base64Dialect;
import io.netty.util.AsciiString;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.text.ParseException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * <p>An authentication token (or "provider authentication token" or "provider token" in Apple's terminology) is a
 * JSON Web Token (JWT) that contains cryptographically-signed claims about the identity of the sender that can be used
 * by APNs clients in lieu of mutual TLS authentication to authenticate with an APNs server. Authentication tokens
 * contain "claims" that identify the development team sending push notifications as well as the specific key used to
 * sign the token.</p>
 *
 * <p>When clients use token-based authentication, they send an authentication token with each push notification. Tokens
 * may expire, in which case clients must discard the old token and generate a new one. Callers generally do
 * <em>not</em> need to interact with authentication tokens directly; {@link com.eatthepath.pushy.apns.ApnsClient} instances
 * using token-based authentication will manage authentication tokens automatically.</p>
 *
 * <p>Tokens may be constructed from an {@link ApnsSigningKey} (for clients sending notifications) or from a
 * Base64-encoded JWT string (for servers verifying a token from a client).</p>
 *
 * <p>Authentication tokens are immutable and thread-safe.</p>
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/establishing_a_token_based_connection_to_apns">Establishing a Token-Based Connection to APNs</a>
 * @see <a href="https://tools.ietf.org/html/rfc7519">RFC 7519 - JSON Web Token (JWT)</a>
 *
 * @see com.eatthepath.pushy.apns.ApnsClientBuilder#setSigningKey(ApnsSigningKey)
 */
public class AuthenticationToken {

    static class AuthenticationTokenHeader {

        private final String keyId;

        AuthenticationTokenHeader(final String keyId) {
            this.keyId = keyId;
        }

        static AuthenticationTokenHeader fromMap(final Map<String, Object> headerMap) {
            if (!headerMap.containsKey("kid") || !(headerMap.get("kid") instanceof String)) {
                throw new IllegalArgumentException("Header map must map a string value to the \"kid\" key.");
            }

            return new AuthenticationTokenHeader((String) headerMap.get("kid"));
        }

        String getKeyId() {
            return this.keyId;
        }

        Map<String, Object> toMap() {
            final Map<String, Object> headerMap = new LinkedHashMap<>(3, 1);
            headerMap.put("alg", "ES256");
            headerMap.put("typ", "JWT");
            headerMap.put("kid", this.keyId);

            return headerMap;
        }
    }

    static class AuthenticationTokenClaims {

        private final String issuer;
        private final Instant issuedAt;

        AuthenticationTokenClaims(final String teamId, final Instant issuedAt) {
            this.issuer = teamId;
            this.issuedAt = issuedAt;
        }

        static AuthenticationTokenClaims fromMap(final Map<String, Object> claimsMap) {
            if (!claimsMap.containsKey("iss") || !(claimsMap.get("iss") instanceof String)) {
                throw new IllegalArgumentException("Claims map must map a string value to the \"iss\" key.");
            }

            if (!claimsMap.containsKey("iat") || !(claimsMap.get("iat") instanceof Long)) {
                throw new IllegalArgumentException("Claims map must map a long value to the \"iat\" key.");
            }

            final String teamId = (String) claimsMap.get("iss");
            final Instant issuedAt = Instant.ofEpochSecond((Long) claimsMap.get("iat"));

            return new AuthenticationTokenClaims(teamId, issuedAt);
        }

        String getIssuer() {
            return this.issuer;
        }

        Instant getIssuedAt() {
            return this.issuedAt;
        }

        Map<String, Object> toMap() {
            final Map<String, Object> headerMap = new LinkedHashMap<>(2, 1);
            headerMap.put("iss", this.issuer);
            headerMap.put("iat", this.issuedAt.getEpochSecond());

            return headerMap;
        }
    }

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
     */
    public AuthenticationToken(final ApnsSigningKey signingKey, final Instant issuedAt) {
        this.header = new AuthenticationTokenHeader(signingKey.getKeyId());
        this.claims = new AuthenticationTokenClaims(signingKey.getTeamId(), issuedAt);

        final String headerJson = JsonSerializer.writeJsonTextAsString(this.header.toMap());
        final String claimsJson = JsonSerializer.writeJsonTextAsString(this.claims.toMap());

        final StringBuilder payloadBuilder = new StringBuilder();
        payloadBuilder.append(encodeUnpaddedBase64UrlString(headerJson.getBytes(StandardCharsets.US_ASCII)));
        payloadBuilder.append('.');
        payloadBuilder.append(encodeUnpaddedBase64UrlString(claimsJson.getBytes(StandardCharsets.US_ASCII)));

        //noinspection TryWithIdenticalCatches
        try {
            final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);
            signature.initSign(signingKey);
            signature.update(payloadBuilder.toString().getBytes(StandardCharsets.US_ASCII));

            this.signatureBytes = signature.sign();
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            // This should never happen because we've already verified that the JVM supports the signing algorithm and
            // that they key is valid at signing key construction time.
            throw new RuntimeException(e);
        } catch (final SignatureException e) {
            // Practically speaking, this should never happen either. Reading through the source, this mainly happens
            // when the Signature object is in the wrong state (we didn't initialize it) or some extremely improbable
            // alignment of random values beyond the caller's control happen. In no case does this (appear to) indicate
            // any condition the caller can meaningfully plan for, control, or respond to.
            throw new RuntimeException(e);
        }

        payloadBuilder.append('.');
        payloadBuilder.append(encodeUnpaddedBase64UrlString(this.signatureBytes));

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

        final JsonParser jsonParser = new JsonParser();

        try {
            this.header = AuthenticationTokenHeader.fromMap(
                    jsonParser.parseJsonObject(
                            new String(decodeBase64UrlEncodedString(jwtSegments[0]), StandardCharsets.US_ASCII)));
        } catch (final ParseException e) {
            throw new IllegalArgumentException("Could not parse header as a JSON object: " +
                    new String(decodeBase64UrlEncodedString(jwtSegments[0]), StandardCharsets.US_ASCII));
        }

        try {
            this.claims = AuthenticationTokenClaims.fromMap(
                    jsonParser.parseJsonObject(
                            new String(decodeBase64UrlEncodedString(jwtSegments[1]), StandardCharsets.US_ASCII)));
        } catch (final ParseException e) {
            throw new IllegalArgumentException("Could not parse claims as a JSON object: " +
                    new String(decodeBase64UrlEncodedString(jwtSegments[1]), StandardCharsets.US_ASCII));
        }

        this.signatureBytes = decodeBase64UrlEncodedString(jwtSegments[2]);
    }

    /**
     * Returns the time at which this token was issued.
     *
     * @return the time at which this token was issued
     */
    public Instant getIssuedAt() {
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
     */
    public boolean verifySignature(final ApnsVerificationKey verificationKey) {
        if (!this.header.getKeyId().equals(verificationKey.getKeyId())) {
            return false;
        }

        if (!this.claims.getIssuer().equals(verificationKey.getTeamId())) {
            return false;
        }

        final byte[] headerAndClaimsBytes;

        final String headerJson = JsonSerializer.writeJsonTextAsString(this.header.toMap());
        final String claimsJson = JsonSerializer.writeJsonTextAsString(this.claims.toMap());

        final String encodedHeaderAndClaims =
                encodeUnpaddedBase64UrlString(headerJson.getBytes(StandardCharsets.US_ASCII)) + '.' +
                encodeUnpaddedBase64UrlString(claimsJson.getBytes(StandardCharsets.US_ASCII));

        headerAndClaimsBytes = encodedHeaderAndClaims.getBytes(StandardCharsets.US_ASCII);

        //noinspection TryWithIdenticalCatches
        try {
            final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);
            signature.initVerify(verificationKey);
            signature.update(headerAndClaimsBytes);

            return signature.verify(this.signatureBytes);
        } catch (final NoSuchAlgorithmException | InvalidKeyException e) {
            // This should never happen because we've already verified that the JVM supports the signing algorithm and
            // that they key is valid at verification key construction time.
            throw new RuntimeException(e);
        } catch (final SignatureException e) {
            // Practically speaking, this should never happen either. Reading through the source, this mainly happens
            // when the Signature object is in the wrong state (we didn't initialize it) or some extremely improbable
            // alignment of random values beyond the caller's control happen. In no case does this (appear to) indicate
            // any condition the caller can meaningfully plan for, control, or respond to.
            throw new RuntimeException(e);
        }
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
            return other.base64EncodedToken == null;
        } else {
            return this.base64EncodedToken.equals(other.base64EncodedToken);
        }
    }

    static String encodeUnpaddedBase64UrlString(final byte[] data) {
        final ByteBuf wrappedString = Unpooled.wrappedBuffer(data);
        final ByteBuf encodedString = Base64.encode(wrappedString, Base64Dialect.URL_SAFE);

        final String encodedUnpaddedString = encodedString.toString(StandardCharsets.US_ASCII).replace("=", "");

        wrappedString.release();
        encodedString.release();

        return encodedUnpaddedString;
    }

    static byte[] decodeBase64UrlEncodedString(final String base64UrlEncodedString) {
        final String paddedBase64UrlEncodedString;

        switch (base64UrlEncodedString.length() % 4) {
            case 2: {
                paddedBase64UrlEncodedString = base64UrlEncodedString + "==";
                break;
            }

            case 3: {
                paddedBase64UrlEncodedString = base64UrlEncodedString + "=";
                break;
            }

            default: {
                paddedBase64UrlEncodedString = base64UrlEncodedString;
            }
        }

        final ByteBuf base64EncodedByteBuf =
                Unpooled.wrappedBuffer(paddedBase64UrlEncodedString.getBytes(StandardCharsets.US_ASCII));

        final ByteBuf decodedByteBuf = Base64.decode(base64EncodedByteBuf, Base64Dialect.URL_SAFE);
        final byte[] decodedBytes = new byte[decodedByteBuf.readableBytes()];

        decodedByteBuf.readBytes(decodedBytes);

        base64EncodedByteBuf.release();
        decodedByteBuf.release();

        return decodedBytes;
    }
}
