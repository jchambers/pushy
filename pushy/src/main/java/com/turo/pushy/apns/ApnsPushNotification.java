/*
 * Copyright (c) 2013-2017 Turo
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

package com.turo.pushy.apns;

import com.turo.pushy.apns.util.ApnsPayloadBuilder;

import java.util.Date;
import java.util.UUID;

/**
 * <p>A push notification that can be sent through the Apple Push Notification service (APNs). Push notifications have a
 * token that identifies the device to which it should be sent, a topic (generally the bundle ID of the receiving app),
 * a JSON payload, and (optionally) a time at which the notification is invalid and should no longer be delivered.</p>
 *
 * <p>Push notifications may also include a unique identifier that will be echoed in responses from the APNs server. If
 * no identifier is provided, the server will assign an identifier automatically.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href=
 *      "https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/APNSOverview.html">
 *      Local and Remote Notification Programming Guide - Apple Push Notification Service</a>
 *
 * @see ApnsPayloadBuilder
 * @see PushNotificationResponse#getApnsId()
 *
 * @since 0.1
 */
public interface ApnsPushNotification {
    /**
     * Returns the token of the device to which this push notification is to be sent.
     *
     * @return a string of hexadecimal digits representing the token of the device to which this push notification is to
     * be sent
     *
     * @since 0.5
     */
    String getToken();

    /**
     * Returns the JSON-encoded payload of this push notification.
     *
     * @return the JSON-encoded payload of this push notification
     *
     * @since 0.1
     */
    String getPayload();

    /**
     * Returns the time at which Apple's push notification service should stop trying to deliver this push notification.
     * If {@code null}, the push notification service will not attempt to store the notification at all. Note that APNs
     * will only store one notification per device token for re-delivery at a time.
     *
     * @return the time at which this notification can be discarded
     *
     * @since 0.5
     */
    Date getExpiration();

    /**
     * Returns the priority with which this push notification should be sent to the receiving device. If {@code null},
     * an immediate delivery priority is assumed.
     *
     * @return the priority with which this push notification should be sent to the receiving device
     *
     * @since 0.4
     */
    DeliveryPriority getPriority();

    /**
     * Returns the topic to which this notification should be sent. This is generally the bundle ID of the receiving
     * app. Topics must not be {@code null}.
     *
     * @return the topic to which this notification should be sent; must not be {@code null}
     *
     * @since 0.5
     */
    String getTopic();

    /**
     * Returns an optional identifier for this notification that allows this notification to supersede previous
     * notifications or to be superseded by later notifications with the same identifier.
     *
     * @return an identifier for this notification; may be {@code null}
     *
     * @since 0.8.1
     */
    String getCollapseId();

    /**
     * Returns the canonical identifier for this push notification. The APNs server will include the given identifier in
     * all responses related to this push notification. If no identifier is provided, the server will assign a unique
     * identifier automatically.
     *
     * @return a unique identifier for this notification; may be {@code null}, in which case the APNs server will assign
     * an identifier automatically
     */
    UUID getApnsId();
}
