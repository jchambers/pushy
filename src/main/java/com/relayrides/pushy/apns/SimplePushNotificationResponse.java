package com.relayrides.pushy.apns;

import java.util.Date;

/**
 * A trivial and immutable implementation of the {@link PushNotificationResponse} interface.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class SimplePushNotificationResponse<T extends ApnsPushNotification> implements PushNotificationResponse<T> {
    private final T pushNotification;
    private final boolean success;
    private final String rejectionReason;
    private final Date tokenExpirationTimestamp;

    public SimplePushNotificationResponse(final T pushNotification, final boolean success, final String rejectionReason, final Date tokenExpirationTimestamp) {
        this.pushNotification = pushNotification;
        this.success = success;
        this.rejectionReason = rejectionReason;
        this.tokenExpirationTimestamp = tokenExpirationTimestamp;
    }

    @Override
    public T getPushNotification() {
        return this.pushNotification;
    }

    @Override
    public boolean isAccepted() {
        return this.success;
    }

    @Override
    public String getRejectionReason() {
        return this.rejectionReason;
    }

    @Override
    public Date getTokenInvalidationTimestamp() {
        return this.tokenExpirationTimestamp;
    }
}
