package com.relayrides.pushy.apns;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;

import org.junit.Before;

public class AuthenticationTokenSupplierTest {

    private static final String ISSUER = "TESTISSUER";
    private static final String KEY_ID = "TESTPKEYID";

    private ECPrivateKey privateKey;

    @Before
    public void setUp() throws Exception {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

        keyPairGenerator.initialize(256, random);

        final KeyPair keyPair = keyPairGenerator.generateKeyPair();
        this.privateKey = (ECPrivateKey) keyPair.getPrivate();
    }

    /* @Test
    public void testAuthenticationTokenSupplier() throws Exception {
        // We're happy here as long as nothing explodes
        new AuthenticationTokenSupplier(ISSUER, KEY_ID, this.privateKey);
    }

    @Test(expected = NullPointerException.class)
    public void testAuthenticationTokenSupplierNullIssuer() throws Exception {
        new AuthenticationTokenSupplier(null, KEY_ID, this.privateKey);
    }

    @Test(expected = NullPointerException.class)
    public void testAuthenticationTokenSupplierNullKeyId() throws Exception {
        new AuthenticationTokenSupplier(ISSUER, null, this.privateKey);
    }

    @Test(expected = NullPointerException.class)
    public void testAuthenticationTokenSupplierNullPrivateKey() throws Exception {
        new AuthenticationTokenSupplier(ISSUER, KEY_ID, null);
    }

    @Test
    public void testGetToken() throws Exception {
        final String token = new AuthenticationTokenSupplier(ISSUER, KEY_ID, this.privateKey).getToken();

        assertTrue(token.length() > 0);
    }

    @Test
    public void testInvalidateToken() throws Exception {
        final AuthenticationTokenSupplier supplier = new AuthenticationTokenSupplier(ISSUER, KEY_ID, this.privateKey);

        final String initialToken = supplier.getToken();

        supplier.invalidateToken("Definitely not the initial token");
        assertEquals(initialToken, supplier.getToken());

        supplier.invalidateToken(initialToken);
        assertNotEquals(initialToken, supplier.getToken());
    } */
}
