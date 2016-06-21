package com.relayrides.pushy.apns;

public class InvalidAuthenticationTokenException extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidAuthenticationTokenException() {
        super();
    }

    public InvalidAuthenticationTokenException(final Throwable cause) {
        super(cause);
    }
}
