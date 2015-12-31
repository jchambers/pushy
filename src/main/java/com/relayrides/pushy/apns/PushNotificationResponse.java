package com.relayrides.pushy.apns;

import java.util.Date;

/**
 * A response from the APNs gateway indicating whether a notification was accepted or rejected.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @param <T> the type of push notification
 */
public interface PushNotificationResponse<T extends ApnsPushNotification> {

    /**
     * Returns the original push notification sent to the APNs gateway.
     *
     * @return the original push notification sent to the APNs gateway
     */
    T getPushNotification();

    /**
     * Indicates whether the push notification was accepted by the APNs gateway.
     *
     * @return {@code true} if the push notification was accepted or {@code false} if it was rejected
     */
    boolean isAccepted();

    /**
     * Returns the reason for rejection reported by the APNs gateway. If the notification was accepted, the rejection
     * reason will be {@code null}.
     *
     * @return the reason for rejection reported by the APNs gateway, or {@code null} if the notification was not
     * rejected
     */
    String getRejectionReason();

    /**
     * If the sent push notification was rejected because the destination token is no longer valid, returns the most
     * recent time at which the APNs gateway confirmed that the token is no longer valid. Callers should stop attempting
     * to send notifications to the expired token unless the token has been re-registered more recently than the
     * returned timestamp.
     *
     * @return the time at which the token for the sent push notification became invalid, or {@code null} if the push
     * notification was either accepted or rejected for a reason other than token invalidation
     */
    Date getTokenInvalidationTimestamp();
}
