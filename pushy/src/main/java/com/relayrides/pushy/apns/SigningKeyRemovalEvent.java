package com.relayrides.pushy.apns;

import java.util.Set;

/**
 * A model object representing indicating the removal of a signing key.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
class SigningKeyRemovalEvent {

    private final Set<String> topicsToClear;

    public SigningKeyRemovalEvent(final Set<String> topicsToClear) {
        this.topicsToClear = topicsToClear;
    }

    public Set<String> getTopicsToClear() {
        return this.topicsToClear;
    }
}
