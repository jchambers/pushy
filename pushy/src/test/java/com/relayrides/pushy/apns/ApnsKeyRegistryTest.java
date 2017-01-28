package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.interfaces.ECPrivateKey;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

public class ApnsKeyRegistryTest {

    private ApnsSigningKey signingKey;

    private static final String KEY_ID = "TESTKEY123";
    private static final String TEAM_ID = "TEAMID0987";

    private static class TestKeyRemovalListener<T extends ApnsKey> implements ApnsKeyRemovalListener<T> {

        private Set<T> removedKeys = new HashSet<>();

        @Override
        public void handleKeyRemoval(final T key) {
            this.removedKeys.add(key);
        }

        public Set<T> getRemovedKeys() {
            return this.removedKeys;
        }
    }

    @Before
    public void setUp() throws Exception {
        this.signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());
    }

    @Test
    public void testRegisterKey() throws Exception {
        // We're happy here as long as nothing explodes
        new ApnsKeyRegistry<>().registerKey(this.signingKey, "topic");
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterKeyNullTopics() throws Exception {
        new ApnsKeyRegistry<>().registerKey(this.signingKey, (String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterKeyNoTopics() throws Exception {
        new ApnsKeyRegistry<>().registerKey(this.signingKey, new String[0]);
    }

    @Test
    public void testGetKeyForTopic() throws Exception {
        final ApnsKeyRegistry<ApnsSigningKey> registry = new ApnsKeyRegistry<>();

        final String topic = "topic";
        registry.registerKey(this.signingKey, topic);

        {
            final Promise<ApnsSigningKey> keyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic, keyPromise);

            assertEquals(this.signingKey, keyPromise.get());

            final Promise<ApnsSigningKey> differentKeyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic + ".different", differentKeyPromise);

            differentKeyPromise.await();

            assertFalse(differentKeyPromise.isSuccess());
            assertTrue(differentKeyPromise.cause() instanceof NoKeyForTopicException);
        }

        final ApnsSigningKey differentKey = new ApnsSigningKey(KEY_ID + "DIFFERENT", TEAM_ID, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());
        registry.registerKey(differentKey, topic);

        {
            final Promise<ApnsSigningKey> keyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic, keyPromise);

            assertEquals(differentKey, keyPromise.get());
        }
    }

    @Test
    public void testRemoveKey() throws Exception {
        final ApnsKeyRegistry<ApnsSigningKey> registry = new ApnsKeyRegistry<>();

        final TestKeyRemovalListener<ApnsSigningKey> listener = new TestKeyRemovalListener<>();
        registry.addKeyRemovalListener(listener);

        final String topic = "topic";

        registry.registerKey(this.signingKey, topic);

        assertTrue(listener.getRemovedKeys().isEmpty());
        registry.removeKey(TEAM_ID, KEY_ID);
        assertTrue(listener.getRemovedKeys().contains(this.signingKey));

        {
            final Promise<ApnsSigningKey> keyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic, keyPromise);

            assertFalse(keyPromise.isSuccess());
            assertTrue(keyPromise.cause() instanceof NoKeyForTopicException);
        }
    }
}
