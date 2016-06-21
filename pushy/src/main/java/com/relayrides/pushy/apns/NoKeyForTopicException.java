package com.relayrides.pushy.apns;

public class NoKeyForTopicException extends Exception {

    private static final long serialVersionUID = 1L;

    public NoKeyForTopicException(final String message) {
        super(message);
    }
}
