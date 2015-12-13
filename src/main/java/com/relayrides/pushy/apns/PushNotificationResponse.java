package com.relayrides.pushy.apns;

public class PushNotificationResponse<T extends ApnsPushNotification> {
    private final T pushNotification;
    private final boolean success;

    public PushNotificationResponse(final T pushNotification, final boolean success) {
        this.pushNotification = pushNotification;
        this.success = success;
    }

    public T getPushNotification() {
        return pushNotification;
    }

    public boolean isSuccess() {
        return success;
    }
}
