package com.relayrides.pushy.apns;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>A registry for keys used to sign or verify APNs authentication tokens. In the APNs model, keys allow signing or
 * verification for all topics associated with a team. Each key has a ten-digit alphanumeric identifier that is unique
 * within the scope of the team to which it belongs, but is not guaranteed to be <em>globally</em> unique.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @param <T> the type of key managed by this registry
 */
class ApnsKeyRegistry<T extends ApnsKey> {
    private final Map<String, T> keysByTopic = new HashMap<>();
    private final Map<String, T> keysByCombinedIdentifier = new HashMap<>();

    public synchronized void registerKey(final T key, final String... topics) throws NoSuchAlgorithmException, InvalidKeyException {
        {
            // This is a little goofy, but bear with me. We want to test all the things that can practically go wrong
            // with a key at registration time so it's not a surprise later. To do that, we create, initialize, and then
            // discard a Signature instance. If something's going to go wrong, it'll go wrong here. This doesn't change
            // any of the language-level guarantees about these exceptions, but it does mean that we can treat them as
            // "unexpected" conceptually farther down the line.
            final Signature signature = Signature.getInstance("SHA256withECDSA");

            if (key instanceof PrivateKey) {
                signature.initSign((PrivateKey) key);
            } else {
                signature.initVerify((PublicKey) key);
            }
        }

        for (final String topic : topics) {
            this.keysByTopic.put(topic, key);
        }

        this.keysByCombinedIdentifier.put(getCombinedIdentifier(key), key);
    }

    public synchronized T getKeyForTopic(final String topic) {
        return this.keysByTopic.get(topic);
    }

    public synchronized T getKeyById(final String teamId, final String keyId) {
        return this.keysByCombinedIdentifier.get(getCombinedIdentifier(teamId, keyId));
    }

    public synchronized T removeKey(final String teamId, final String keyId) {
        final T removedApnsKey = this.keysByCombinedIdentifier.remove(getCombinedIdentifier(teamId, keyId));

        if (removedApnsKey != null) {
            final List<String> topicsToClear = new ArrayList<>();

            for (final Map.Entry<String, T> entry : this.keysByTopic.entrySet()) {
                if (removedApnsKey.equals(entry.getValue())) {
                    topicsToClear.add(entry.getKey());
                }
            }

            for (final String topic : topicsToClear) {
                this.keysByTopic.remove(topic);
            }
        }

        return removedApnsKey;
    }

    static String getCombinedIdentifier(final ApnsKey key) {
        return getCombinedIdentifier(key.getTeamId(), key.getKeyId());
    }

    static String getCombinedIdentifier(final String teamId, final String keyId) {
        return teamId + "." + keyId;
    }
}
