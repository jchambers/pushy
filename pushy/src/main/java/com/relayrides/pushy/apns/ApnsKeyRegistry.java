package com.relayrides.pushy.apns;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.netty.util.concurrent.Promise;

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
public class ApnsKeyRegistry<T extends ApnsKey> implements ApnsKeySource<T> {
    private final Map<String, T> keysByTopic = new HashMap<>();
    private final List<ApnsKeyRemovalListener<T>> keyRemovalListeners = new ArrayList<>();

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
    public void registerKey(final T key, final String... topics) throws NoSuchAlgorithmException, InvalidKeyException {
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

        synchronized (this.keysByTopic) {
            for (final String topic : topics) {
                this.keysByTopic.put(topic, key);
            }
        }
    }

    @Override
    public void getKeyForTopic(final String topic, final Promise<T> keyPromise) {
        final T apnsKey;

        synchronized (this.keysByTopic) {
            apnsKey = this.keysByTopic.get(topic);
        }

        if (apnsKey != null) {
            keyPromise.setSuccess(apnsKey);
        } else {
            keyPromise.setFailure(new NoKeyForTopicException("No key found for topic: " + topic));
        }
    }

    @Override
    public void addKeyRemovalListener(final ApnsKeyRemovalListener<T> listener) {
        synchronized (this.keyRemovalListeners) {
            this.keyRemovalListeners.add(listener);
        }
    }

    @Override
    public boolean removeKeyRemovalListener(final ApnsKeyRemovalListener<T> listener) {
        synchronized (this.keyRemovalListeners) {
            return this.keyRemovalListeners.remove(listener);
        }
    }

    /**
     * Removes a key from the registry and clears all topic associations with that key.
     *
     * @param teamId the ten-digit team identifier of the key to remove
     * @param keyId the ten-digit key identifier of the key to remove
     *
     * @return the removed key, or {@code null} if no key was found for the given team and key identifiers
     */
    public T removeKey(final String teamId, final String keyId) {
        final Set<String> topicsToClear = new HashSet<>();

        T lastKeyFound = null;

        synchronized (this.keysByTopic) {
            for (final Map.Entry<String, T> entry : this.keysByTopic.entrySet()) {
                lastKeyFound = entry.getValue();

                if (lastKeyFound.getTeamId().equals(teamId) && lastKeyFound.getKeyId().equals(keyId)) {
                    topicsToClear.add(entry.getKey());
                }
            }

            for (final String topic : topicsToClear) {
                this.keysByTopic.remove(topic);
            }
        }

        synchronized (this.keyRemovalListeners) {
            if (lastKeyFound != null) {
                for (final ApnsKeyRemovalListener<T> listener : this.keyRemovalListeners) {
                    listener.handleKeyRemoval(lastKeyFound);
                }
            }
        }

        return lastKeyFound;
    }

    public void clear() {
        final Collection<T> removedKeys;

        synchronized (this.keysByTopic) {
            removedKeys = this.keysByTopic.values();
            this.keysByTopic.clear();
        }

        synchronized (this.keyRemovalListeners) {
            for (final T removedKey : removedKeys) {
                for (final ApnsKeyRemovalListener<T> listener : this.keyRemovalListeners) {
                    listener.handleKeyRemoval(removedKey);
                }
            }
        }
    }
}
