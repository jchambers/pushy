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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;


public class AuthenticationTokenTest {

    private ApnsSigningKey signingKey;
    private ApnsVerificationKey verificationKey;

    private static final String KEY_ID = "TESTKEY123";
    private static final String TEAM_ID = "TEAMID0987";

    @BeforeEach
    public void setUp() throws Exception {
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        this.signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        this.verificationKey = new ApnsVerificationKey(KEY_ID, TEAM_ID, (ECPublicKey) keyPair.getPublic());
    }

    @Test
    void testHeaderFromMap() {
        assertThrows(IllegalArgumentException.class, () ->
                AuthenticationToken.AuthenticationTokenHeader.fromMap(Collections.emptyMap()));

        assertThrows(IllegalArgumentException.class, () ->
                AuthenticationToken.AuthenticationTokenHeader.fromMap(
                        Collections.singletonMap("kid", false)));

        final String keyId = "test-key-id";

        final Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("alg", "ES256");
        headerMap.put("typ", "JWT");
        headerMap.put("kid", keyId);

        final AuthenticationToken.AuthenticationTokenHeader authenticationTokenHeader =
                AuthenticationToken.AuthenticationTokenHeader.fromMap(headerMap);

        assertEquals(keyId, authenticationTokenHeader.getKeyId());
    }

    @Test
    void testHeadersToMap() {
        final String keyId = "test-key";

        final Map<String, Object> headerMap = new HashMap<>();
        headerMap.put("alg", "ES256");
        headerMap.put("typ", "JWT");
        headerMap.put("kid", keyId);

        assertEquals(headerMap, new AuthenticationToken.AuthenticationTokenHeader(keyId).toMap());
    }

    @Test
    void testClaimsFromMap() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthenticationToken.AuthenticationTokenClaims.fromMap(
                        Collections.emptyMap()));

        assertThrows(IllegalArgumentException.class,
                () -> AuthenticationToken.AuthenticationTokenClaims.fromMap(
                        Collections.singletonMap("iss", "team-id")));

        assertThrows(IllegalArgumentException.class,
                () -> AuthenticationToken.AuthenticationTokenClaims.fromMap(
                        Collections.singletonMap("iat", Instant.now().getEpochSecond())));

        {
            final Map<String, Object> badIssuerMap = new HashMap<>();
            badIssuerMap.put("iss", false);
            badIssuerMap.put("iat", Instant.now().getEpochSecond());

            assertThrows(IllegalArgumentException.class,
                    () -> AuthenticationToken.AuthenticationTokenClaims.fromMap(
                            badIssuerMap));
        }

        {
            final Map<String, Object> badTimestampMap = new HashMap<>();
            badTimestampMap.put("iss", "team-id");
            badTimestampMap.put("iat", "soon");

            assertThrows(IllegalArgumentException.class,
                    () -> AuthenticationToken.AuthenticationTokenClaims.fromMap(
                            badTimestampMap));
        }

        {
            final String teamId = "team-id";

            // Hack: make sure we don't have a millisecond component
            final Instant timestamp = Instant.ofEpochSecond(Instant.now().getEpochSecond());

            final Map<String, Object> claimsMap = new HashMap<>();
            claimsMap.put("iss", teamId);
            claimsMap.put("iat", timestamp.getEpochSecond());

            final AuthenticationToken.AuthenticationTokenClaims claims =
                    AuthenticationToken.AuthenticationTokenClaims.fromMap(claimsMap);

            assertEquals(teamId, claims.getIssuer());
            assertEquals(timestamp, claims.getIssuedAt());
        }
    }

    @Test
    void testClaimsToMap() {
        final String teamId = "team-id";

        // Hack: make sure we don't have a millisecond component
        final Instant timestamp = Instant.ofEpochSecond(Instant.now().getEpochSecond());

        final Map<String, Object> claimsMap = new HashMap<>();
        claimsMap.put("iss", teamId);
        claimsMap.put("iat", timestamp.getEpochSecond());

        assertEquals(claimsMap, new AuthenticationToken.AuthenticationTokenClaims(teamId, timestamp).toMap());
    }

    @Test
    void testAuthenticationTokenFromSigningKey() {
        assertDoesNotThrow(() -> new AuthenticationToken(this.signingKey, Instant.now()));
    }

    @Test
    void testAuthenticationTokenFromString() throws Exception {
        final String base64EncodedToken = new AuthenticationToken(this.signingKey, Instant.now()).toString();

        assertDoesNotThrow(() -> new AuthenticationToken(base64EncodedToken));
    }

    @Test
    void testGetIssuedAt() throws Exception {
        final Instant now = Instant.now();
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, now);

        assertEquals(token.getIssuedAt(), now);
    }

    @Test
    void testVerifySignature() throws Exception {
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, Instant.now());

        assertTrue(token.verifySignature(this.verificationKey));

        {
            final ApnsVerificationKey keyWithWrongKeyId =
                    new ApnsVerificationKey(KEY_ID + "NOPE", TEAM_ID, this.verificationKey);

            assertFalse(token.verifySignature(keyWithWrongKeyId));
        }

        {
            final ApnsVerificationKey keyWithWrongTeamId =
                    new ApnsVerificationKey(KEY_ID, TEAM_ID + "NOPE", this.verificationKey);

            assertFalse(token.verifySignature(keyWithWrongTeamId));
        }

        {
            // This isn't teeeeeeeechnically guaranteed to be a mismatched key, but the odds of randomly generating the
            // same key twice are small enough that we can let it slide.
            final KeyPair keyPair = KeyPairUtil.generateKeyPair();

            final ApnsVerificationKey keyWithWrongPublicKey =
                    new ApnsVerificationKey(KEY_ID, TEAM_ID, (ECPublicKey) keyPair.getPublic());

            assertFalse(token.verifySignature(keyWithWrongPublicKey));
        }
    }

    @Test
    void testToString() throws Exception {
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, Instant.now());

        assertTrue(Pattern.matches("^[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+$", token.toString()));
    }

    @Test
    void testEncodeDecodeBase64() {
        final byte[] originalBytes =
                "We expect to get these bytes back after encoding, then decoding as Base64.".getBytes();

        final String encodedString = AuthenticationToken.encodeUnpaddedBase64UrlString(originalBytes);

        assertArrayEquals(originalBytes, AuthenticationToken.decodeBase64UrlEncodedString(encodedString));
    }
}
