package com.relayrides.pushy.apns;

/**
 * A model object representing indicating the removal of a signing key.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
class SigningKeyRemovalEvent {

    private final ApnsSigningKey key;

    public SigningKeyRemovalEvent(final ApnsSigningKey key) {
        this.key = key;
    }

    public ApnsSigningKey getKey() {
        return this.key;
    }
}
