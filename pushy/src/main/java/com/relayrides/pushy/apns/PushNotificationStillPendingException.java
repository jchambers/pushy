package com.relayrides.pushy.apns;

/**
 * An exception that indicates that an attempt to send a notification failed because the same notification has already
 * been sent, but has not yet been resolved.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
public class PushNotificationStillPendingException extends Exception {
    private static final long serialVersionUID = 1L;
}
