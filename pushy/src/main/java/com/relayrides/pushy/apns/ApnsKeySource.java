package com.relayrides.pushy.apns;

import io.netty.util.concurrent.Promise;

public interface ApnsKeySource<T extends ApnsKey> {

    /**
     * Retrieves an APNs key for the given topic and notifies the given promise of the result. Implementations of this
     * method must never block; instead, blocking operations should be delegated to separate threads. When a key has
     * been retrieved (or a key retrieval attempt has failed), implementations must notify the given {@code Promise} of
     * the result. If no key could be found for the given topic, implementations should mark the given {@code Promise}
     * as failed with a {@link NoKeyForTopicException}.
     *
     * @param topic the topic for which to retrieve a key
     * @param keyPromise the promise to be notified when a key is retrieved (or when a retrieval attempt fails)
     *
     * @see Promise#trySuccess(Object)
     * @see Promise#setSuccess(Object)
     * @see Promise#tryFailure(Throwable)
     * @see Promise#setFailure(Throwable)
     * @see NoKeyForTopicException
     */
    void getKeyForTopic(String topic, Promise<T> keyPromise);

    /**
     * Adds a listener to be notified when keys are removed from this key source. Key source implementations must notify
     * all listeners when a key is removed so listeners can discard any cached signatures associated with that key.
     *
     * @param listener the listener to add to this key source
     */
    void addKeyRemovalListener(ApnsKeyRemovalListener<T> listener);

    /**
     * Removes a key removal listener from this key source.
     *
     * @param listener the listener to remove
     *
     * @return {@code true} if the given listener was previously registered with this key source and removed or
     * {@code false} otherwise
     */
    boolean removeKeyRemovalListener(ApnsKeyRemovalListener<T> listener);
}
