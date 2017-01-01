package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.security.interfaces.ECPrivateKey;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class ApnsKeyRegistryTest {

    private ApnsSigningKey signingKey;

    private static final String KEY_ID = "TESTKEY123";
    private static final String TEAM_ID = "TEAMID0987";

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

        assertEquals(this.signingKey, registry.getKeyForTopic(topic));
        assertNull(registry.getKeyForTopic(topic + ".different"));

        final ApnsSigningKey differentKey = new ApnsSigningKey(KEY_ID + "DIFFERENT", TEAM_ID, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());

        registry.registerKey(differentKey, topic);

        assertEquals(differentKey, registry.getKeyForTopic(topic));
    }

    @Test
    public void testGetKeyById() throws Exception {
        final ApnsKeyRegistry<ApnsSigningKey> registry = new ApnsKeyRegistry<>();

        registry.registerKey(this.signingKey, "topic");

        assertEquals(this.signingKey, registry.getKeyById(TEAM_ID, KEY_ID));
        assertNull(registry.getKeyById(TEAM_ID + "DIFFERENT", KEY_ID));
        assertNull(registry.getKeyById(TEAM_ID, KEY_ID + "DIFFERENT"));
    }

    @Test
    public void testRemoveKey() throws Exception {
        final ApnsKeyRegistry<ApnsSigningKey> registry = new ApnsKeyRegistry<>();

        final String topic = "topic";
        final String secondTopic = "topic.second";

        registry.registerKey(this.signingKey, topic, secondTopic);

        {
            final Set<String> topics = registry.removeKey(TEAM_ID, KEY_ID);

            assertEquals(2, topics.size());
            assertTrue(topics.contains(topic));
            assertTrue(topics.contains(secondTopic));
        }

        final ApnsSigningKey differentKey = new ApnsSigningKey(KEY_ID + "DIFFERENT", TEAM_ID, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());

        registry.registerKey(this.signingKey, topic, secondTopic);
        registry.registerKey(differentKey, topic);

        {
            final Set<String> topics = registry.removeKey(TEAM_ID, KEY_ID);

            assertEquals(1, topics.size());
            assertTrue(topics.contains(secondTopic));
        }
    }
}
