package com.relayrides.pushy.apns;

import java.util.Date;

public class PushNotificationResponse<T extends ApnsPushNotification> {
    private final T pushNotification;
    private final boolean success;
    private final String rejectionReason;
    private final Date tokenExpirationTimestamp;

    public PushNotificationResponse(final T pushNotification, final boolean success, final String rejectionReason, final Date tokenExpirationTimestamp) {
        this.pushNotification = pushNotification;
        this.success = success;
        this.rejectionReason = rejectionReason;
        this.tokenExpirationTimestamp = tokenExpirationTimestamp;
    }

    public T getPushNotification() {
        return this.pushNotification;
    }

    public boolean isSuccess() {
        return this.success;
    }

    public String getRejectionReason() {
        return this.rejectionReason;
    }

    public Date getTokenExpirationTimestamp() {
        return this.tokenExpirationTimestamp;
    }
}
