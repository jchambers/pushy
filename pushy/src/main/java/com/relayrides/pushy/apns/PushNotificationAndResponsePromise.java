package com.relayrides.pushy.apns;

import io.netty.util.concurrent.Promise;

/**
 * A for-internal-use-only ttuple of a push notification and a {@link Promise} to be notified with the outcome of the
 * attempt to send the notification.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.10
 */
class PushNotificationAndResponsePromise {
    private final ApnsPushNotification pushNotification;
    private final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise;

    public PushNotificationAndResponsePromise(final ApnsPushNotification pushNotification, final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise) {
        this.pushNotification = pushNotification;
        this.responsePromise = responsePromise;
    }

    public ApnsPushNotification getPushNotification() {
        return this.pushNotification;
    }

    public Promise<PushNotificationResponse<ApnsPushNotification>> getResponsePromise() {
        return this.responsePromise;
    }
}
