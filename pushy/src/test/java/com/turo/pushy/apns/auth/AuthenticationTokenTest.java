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

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Date;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class AuthenticationTokenTest {

    private ApnsSigningKey signingKey;
    private ApnsVerificationKey verificationKey;

    private static final String KEY_ID = "TESTKEY123";
    private static final String TEAM_ID = "TEAMID0987";

    @Before
    public void setUp() throws Exception {
        final KeyPair keyPair = KeyPairUtil.generateKeyPair();

        this.signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPair.getPrivate());
        this.verificationKey = new ApnsVerificationKey(KEY_ID, TEAM_ID, (ECPublicKey) keyPair.getPublic());
    }

    @Test
    public void testAuthenticationTokenFromSigningKey() throws Exception {
        // We're happy here as long as nothing explodes
        new AuthenticationToken(this.signingKey, new Date());
    }

    @Test
    public void testAuthenticationTokenFromString() throws Exception {
        final String base64EncodedToken = new AuthenticationToken(this.signingKey, new Date()).toString();

        // We're happy here as long as nothing explodes
        new AuthenticationToken(base64EncodedToken);
    }

    @Test
    public void testGetIssuedAt() throws Exception {
        final Date now = new Date();
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, now);

        assertEquals(token.getIssuedAt(), now);
    }

    @Test
    public void testVerifySignature() throws Exception {
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, new Date());

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
    public void testToString() throws Exception {
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, new Date());

        assertTrue(Pattern.matches("^[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9_\\-]+$", token.toString()));
    }
}
