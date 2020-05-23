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
     * Returns the reason for rejection reported by the APNs gateway. If the notification was accepted, the rejection
     * reason will be {@code null}.
     *
     * @return the reason for rejection reported by the APNs gateway, or {@code null} if the notification was not
     * rejected
     *
     * @since 0.5
     */
    String getRejectionReason();

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
