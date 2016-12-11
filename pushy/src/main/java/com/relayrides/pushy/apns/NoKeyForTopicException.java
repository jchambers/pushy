package com.relayrides.pushy.apns;

/**
 * An exception thrown to indicate that an APNs client using token-based authentication could not find a signing key for
 * a notification's topic.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.9
 */
public class NoKeyForTopicException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new exception with the given explanatory message.
     *
     * @param message a short, human-readable explanation of the cause of this exception
     */
    public NoKeyForTopicException(final String message) {
        super(message);
    }
}
