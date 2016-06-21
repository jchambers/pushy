package com.relayrides.pushy.apns;

public class NoSigningKeyForTopicException extends Exception {

    private static final long serialVersionUID = 1L;

    public NoSigningKeyForTopicException(final String message) {
        super(message);
    }
}
