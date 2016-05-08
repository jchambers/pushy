/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns;

import java.util.Date;

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
     * If the sent push notification was rejected because the destination token is no longer valid, returns the most
     * recent time at which the APNs gateway confirmed that the token is no longer valid. Callers should stop attempting
     * to send notifications to the expired token unless the token has been re-registered more recently than the
     * returned timestamp.
     *
     * @return the time at which the token for the sent push notification became invalid, or {@code null} if the push
     * notification was either accepted or rejected for a reason other than token invalidation
     *
     * @since 0.5
     */
    Date getTokenInvalidationTimestamp();
}
