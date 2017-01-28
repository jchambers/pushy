package com.relayrides.pushy.apns;

/**
 * A model object representing indicating the removal of a signing key.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
class ApnsKeyRemovalEvent<T extends ApnsKey> {

    private final T removedKey;

    public ApnsKeyRemovalEvent(final T removedKey) {
        this.removedKey = removedKey;
    }

    public T getRemovedKey() {
        return this.removedKey;
    }
}
