/*
 * Copyright (c) 2020 Jon Chambers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.eatthepath.pushy.apns;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A response from the APNs gateway indicating whether a notification was accepted or rejected.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @param <T> the type of push notification
 *
 * @since 0.5
 */
public interface PushNotificationResponse<T extends ApnsPushNotification> {

    /**
     * Returns the original push notification sent to the APNs gateway.
     *
     * @return the original push notification sent to the APNs gateway
     *
     * @since 0.5
     */
    T getPushNotification();

    /**
     * Indicates whether the push notification was accepted by the APNs gateway.
     *
     * @return {@code true} if the push notification was accepted or {@code false} if it was rejected
     *
     * @since 0.5
     */
    boolean isAccepted();

    /**
     * Returns the ID assigned to this push notification by the APNs server.
     *
     * @return the ID assigned to this push notification by the APNs server
     */
    UUID getApnsId();


    /**
     * Returns a unique identifier set by the APNs server in development environments to facilitate testing
     * notifications. Note that this identifier is distinct from the identifier returned by {@link #getApnsId()}.
     *
     * @return the unique identifier assigned by the APNs server to the sent notification if the server is in the APNs
     * development environment or empty if the server is in the production environment and did not include a unique
     * identifier
     *
     * @see ApnsClientBuilder#setApnsServer(String)
     * @see ApnsClientBuilder#DEVELOPMENT_APNS_HOST
     * @see <a href="https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/handling_notification_responses_from_apns#3394541">Handling notification responses from APNs - Interpret header responses</a>
     * @see <a href="https://developer.apple.com/documentation/usernotifications/testing_notifications_using_the_push_notification_console">Testing notifications using the Push Notification Console</a>
     */
    default Optional<UUID> getApnsUniqueId() {
        return Optional.empty();
    }

    /**
     * Returns the HTTP status code reported by the APNs server.
     *
     * @return the HTTP status code reported by the APNs server
     *
     * @since 0.15.0
     */
    int getStatusCode();

    /**
     * Returns the reason for rejection reported by the APNs gateway. If the notification was accepted, the rejection
     * reason will be {@code null}.
     *
     * @return the reason for rejection reported by the APNs gateway, or empty if the notification was not rejected
     *
     * @since 0.5
     */
    Optional<String> getRejectionReason();

    /**
     * If the sent push notification was rejected because the destination token is no longer valid, returns "the time at
     * which APNs confirmed the token was no longer valid for the topic." Callers should stop attempting
     * to send notifications to the expired token unless the token has been re-registered more recently than the
     * returned timestamp.
     *
     * @return the time at which APNs confirmed the token was no longer valid for the given topic, or empty if the push
     * notification was either accepted or rejected for a reason other than token invalidation
     *
     * @see <a href="https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/sending_notification_requests_to_apns#2947616">Sending
     * Notification Requests to APNs</a>
     *
     * @since 0.5
     */
    Optional<Instant> getTokenInvalidationTimestamp();
}
