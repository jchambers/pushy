package com.relayrides.pushy.apns;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <p>A registry for keys used to sign or verify APNs authentication tokens. In the APNs model, keys allow signing or
 * verification for all topics associated with a team. Each key has a ten-digit alphanumeric identifier that is unique
 * within the scope of the team to which it belongs, but is not guaranteed to be <em>globally</em> unique.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @param <T> the type of key managed by this registry
 *
 * @since 0.10
 */
class ApnsKeyRegistry<T extends ApnsKey> {
    private final Map<String, T> keysByTopic = new HashMap<>();
    private final Map<String, T> keysByCombinedIdentifier = new HashMap<>();

    /**
     * Associates a key with a set of topics and checks validity of the key. If another key was previously associated
     * with one of the given topics, the old association will be discarded and the topic will be associated with the
     * given key instead.
     *
     * @param key the key to register
     * @param topics the topics with which the new key should be associated
     *
     * @throws NoSuchAlgorithmException if the JRE does not support the signature algorithm required by APNs
     * ({@value ApnsKey#APNS_SIGNATURE_ALGORITHM})
     * @throws InvalidKeyException if the given key is invalid for any reason
     */
    public synchronized void registerKey(final T key, final String... topics) throws NoSuchAlgorithmException, InvalidKeyException {
        Objects.requireNonNull(topics, "Topics must not be null");

        if (topics.length == 0) {
            throw new IllegalArgumentException("Topics must not be empty");
        }

        {
            // This is a little goofy, but bear with me. We want to test all the things that can practically go wrong
            // with a key at registration time so it's not a surprise later. To do that, we create, initialize, and then
            // discard a Signature instance. If something's going to go wrong, it'll go wrong here. This doesn't change
            // any of the language-level guarantees about these exceptions, but it does mean that we can treat them as
            // "unexpected" conceptually farther down the line.
            final Signature signature = Signature.getInstance(ApnsKey.APNS_SIGNATURE_ALGORITHM);

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

    /**
     * Returns the key associated with the given topic.
     *
     * @param topic the topic for which to find a key
     *
     * @return the key associated with the given topic, or {@code null} if no key is associated with the given topic
     */
    public synchronized T getKeyForTopic(final String topic) {
        return this.keysByTopic.get(topic);
    }

    /**
     * Returns the key registered with the given team identifier and key identifier.
     *
     * @param teamId the ten-digit team identifier of the key to retrieve
     * @param keyId the ten-digit key identifier of the key to retrieve
     *
     * @return the key registered with the given team and key identifier, or {@code null} if no key with the given
     * identifiers is registered
     */
    public synchronized T getKeyById(final String teamId, final String keyId) {
        return this.keysByCombinedIdentifier.get(getCombinedIdentifier(teamId, keyId));
    }

    /**
     * Removes a key from the registry and clears all topic associations with that key.
     *
     * @param teamId the ten-digit team identifier of the key to remove
     * @param keyId the ten-digit key identifier of the key to remove
     *
     * @return the topics previously associated with the removed key; may be empty if no key was registered with the
     * given identifiers
     */
    public synchronized Set<String> removeKey(final String teamId, final String keyId) {
        final Set<String> topicsToClear = new HashSet<>();

        if (this.keysByCombinedIdentifier.remove(getCombinedIdentifier(teamId, keyId)) != null) {
            for (final Map.Entry<String, T> entry : this.keysByTopic.entrySet()) {
                final T apnsKey = entry.getValue();

                if (apnsKey.getTeamId().equals(teamId) && apnsKey.getKeyId().equals(keyId)) {
                    topicsToClear.add(entry.getKey());
                }
            }

            for (final String topic : topicsToClear) {
                this.keysByTopic.remove(topic);
            }
        }

        return topicsToClear;
    }

    public synchronized void clear() {
        this.keysByTopic.clear();
        this.keysByCombinedIdentifier.clear();
    }

    static String getCombinedIdentifier(final ApnsKey key) {
        return getCombinedIdentifier(key.getTeamId(), key.getKeyId());
    }

    static String getCombinedIdentifier(final String teamId, final String keyId) {
        return teamId + "." + keyId;
    }
}
