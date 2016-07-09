package com.relayrides.pushy.apns;

/**
 * An exception that indicates that a push notification could not be sent due to an upstream server error. Server errors
 * should be considered temporary failures, and callers should attempt to send the notification again later.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 */
public class ApnsServerException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new APNs server exception.
     *
     * @param message a message from the server (if any) explaining the error; may be {@code null}
     */
    public ApnsServerException(final String message) {
        super(message);
    }
}
