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

import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;

/**
 * <p>
 * A push notification that can be sent through the Apple Push Notification service (APNs). Push notifications have a
 * token that identifies the device to which it should be sent, a topic (generally the bundle ID of the receiving app),
 * a JSON payload, and (optionally) a time at which the notification is invalid and should no longer be delivered.
 * </p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href=
 *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html">
 *      Local and Remote Notification Programming Guide - Apple Push Notification Service</a>
 *
 * @see ApnsPayloadBuilder
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
     * @see <a href=
     *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
     *      APNs Provider API</a>
     *
     * @since 0.5
     */
    String getToken();

    /**
     * Returns the JSON-encoded payload of this push notification.
     *
     * @return the JSON-encoded payload of this push notification
     *
     * @see <a href=
     *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/TheNotificationPayload.html#//apple_ref/doc/uid/TP40008194-CH107-SW1">
     *      The Remote Notification Payload</a>
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
     * @see <a href=
     *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
     *      APNs Provider API</a>
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
     * @see <a href=
     *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
     *      APNs Provider API</a>
     *
     * @since 0.4
     */
    DeliveryPriority getPriority();

    /**
     * <p>
     * Returns the topic to which this notification should be sent. This is generally the bundle ID of the receiving
     * app.
     * </p>
     *
     * <p>
     * Some (older) APNs certificates contain only a single topic; if this push notification is sent via a client using
     * a single-topic certificate, this topic may be {@code null}, in which case the APNs gateway will use the
     * certificate's subject (the bundle ID for the receiving app) as the topic.
     * </p>
     *
     * @return the topic to which this notification should be sent, or {@code null} for the default topic if this
     *         notification is sent on a connection with a single-topic certificate
     *
     * @see <a href=
     *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW1">
     *      APNs Provider API</a>
     *
     * @since 0.5
     */
    String getTopic();
}
