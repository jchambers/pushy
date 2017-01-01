package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Date;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

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
    public void testGetExpiration() throws Exception {
        final Date now = new Date();
        final AuthenticationToken token = new AuthenticationToken(this.signingKey, now);

        assertTrue(token.getExpiration().after(now));
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

        assertTrue(Pattern.matches("^[a-zA-Z0-9+\\-]+\\.[a-zA-Z0-9+\\-]+\\.[a-zA-Z0-9+\\-]+$", token.toString()));
    }
}
