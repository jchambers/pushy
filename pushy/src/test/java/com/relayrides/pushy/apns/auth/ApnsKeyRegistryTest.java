package com.relayrides.pushy.apns.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.relayrides.pushy.apns.NoKeyForTopicException;
import com.relayrides.pushy.apns.auth.ApnsKey;
import com.relayrides.pushy.apns.auth.ApnsKeyRegistry;
import com.relayrides.pushy.apns.auth.ApnsKeyRemovalListener;

import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.Promise;

public abstract class ApnsKeyRegistryTest<T extends ApnsKey> {

    private static final String KEY_ID = "TESTKEY123";
    private static final String TEAM_ID = "TEAMID0987";

    private static class TestKeyRemovalListener<K extends ApnsKey> implements ApnsKeyRemovalListener<K> {

        private Set<K> removedKeys = new HashSet<>();

        @Override
        public void handleKeyRemoval(final K key) {
            this.removedKeys.add(key);
        }

        public Set<K> getRemovedKeys() {
            return this.removedKeys;
        }
    }

    protected abstract ApnsKeyRegistry<T> getNewRegistry();

    protected abstract T getNewKey(final String keyId, final String teamId) throws NoSuchAlgorithmException;

    @Test
    public void testRegisterKey() throws Exception {
        // We're happy here as long as nothing explodes
        this.getNewRegistry().registerKey(this.getNewKey(KEY_ID, TEAM_ID), "topic");
    }

    @Test(expected = NullPointerException.class)
    public void testRegisterKeyNullTopics() throws Exception {
        this.getNewRegistry().registerKey(this.getNewKey(KEY_ID, TEAM_ID), (String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterKeyNoTopics() throws Exception {
        this.getNewRegistry().registerKey(this.getNewKey(KEY_ID, TEAM_ID), new String[0]);
    }

    @Test
    public void testGetKeyForTopic() throws Exception {
        final ApnsKeyRegistry<T> registry = this.getNewRegistry();

        final T key = this.getNewKey(KEY_ID, TEAM_ID);

        final String topic = "topic";
        registry.registerKey(key, topic);

        {
            final Promise<T> keyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic, keyPromise);

            assertEquals(key, keyPromise.get());

            final Promise<T> differentKeyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic + ".different", differentKeyPromise);

            differentKeyPromise.await();

            assertFalse(differentKeyPromise.isSuccess());
            assertTrue(differentKeyPromise.cause() instanceof NoKeyForTopicException);
        }

        final T differentKey = this.getNewKey(KEY_ID + "DIFFERENT", TEAM_ID);
        registry.registerKey(differentKey, topic);

        {
            final Promise<T> keyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic, keyPromise);

            assertEquals(differentKey, keyPromise.get());
        }
    }

    @Test
    public void testRemoveKey() throws Exception {
        final ApnsKeyRegistry<T> registry = this.getNewRegistry();

        final TestKeyRemovalListener<T> listener = new TestKeyRemovalListener<>();
        registry.addKeyRemovalListener(listener);

        final String topic = "topic";
        final T key = this.getNewKey(KEY_ID, TEAM_ID);

        registry.registerKey(key, topic);

        assertTrue(listener.getRemovedKeys().isEmpty());
        registry.removeKey(TEAM_ID, KEY_ID);
        assertTrue(listener.getRemovedKeys().contains(key));

        {
            final Promise<T> keyPromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            registry.getKeyForTopic(topic, keyPromise);

            assertFalse(keyPromise.isSuccess());
            assertTrue(keyPromise.cause() instanceof NoKeyForTopicException);
        }
    }
}
