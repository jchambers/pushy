package com.relayrides.pushy.apns.auth;

import java.io.File;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;

import org.junit.Test;

import com.relayrides.pushy.apns.ApnsClientTest;
import com.relayrides.pushy.apns.auth.ApnsKeyRegistry;
import com.relayrides.pushy.apns.auth.ApnsSigningKey;
import com.relayrides.pushy.apns.auth.ApnsSigningKeyRegistry;

public class ApnsSigningKeyRegistryTest extends ApnsKeyRegistryTest<ApnsSigningKey> {

    private static final String PRIVATE_KEY_FILENAME = "/token-auth-private-key.p8";

    @Override
    protected ApnsKeyRegistry<ApnsSigningKey> getNewRegistry() {
        return new ApnsSigningKeyRegistry();
    }

    @Override
    protected ApnsSigningKey getNewKey(final String keyId, final String teamId) throws NoSuchAlgorithmException {
        return new ApnsSigningKey(keyId, teamId, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());
    }

    @Test
    public void testRegisterSigningKeyFromInputStream() throws Exception {
        try (final InputStream privateKeyInputStream = ApnsSigningKeyRegistryTest.class.getResourceAsStream(PRIVATE_KEY_FILENAME)) {
            // We're happy here as long as nothing explodes
            this.getNewRegistry().registerKey(privateKeyInputStream, "team-id", "key-id", "topic");
        }
    }

    @Test
    public void testRegisterSigningKeyFromFile() throws Exception {
        final File privateKeyFile = new File(ApnsClientTest.class.getResource(PRIVATE_KEY_FILENAME).getFile());

        // We're happy here as long as nothing explodes
        this.getNewRegistry().registerKey(privateKeyFile, "team-id", "key-id", "topic");
    }
}
