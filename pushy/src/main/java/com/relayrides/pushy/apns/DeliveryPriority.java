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

/**
 * An enumeration of delivery priorities for APNs push notifications. Note that this priority affects when the
 * notification may be delivered to the receiving device by the APNs gateway and does <em>not</em> affect when the
 * notification will be sent to the gateway itself.
 *
 * @see <a href=
 *      "https://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/APNsProviderAPI.html#//apple_ref/doc/uid/TP40008194-CH101-SW16">
 *      Local and Remote Notification Programming Guide - APNs Provider API - Notification API - Request</a>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.4
 */
public enum DeliveryPriority {

    /**
     * <p>Indicates that the APNs server should attempt to deliver a notification immediately. Additionally, according
     * to Apple's documentation:</p>
     *
     * <blockquote><p>The push notification must trigger an alert, sound, or badge on the device. It is an error to use
     * this priority for a push that contains only the {@code content-available} key.</p></blockquote>
     */
    IMMEDIATE(10),

    /**
     * <p>Indicates that the APNs server should attempt to deliver a notification "at a time that conserves power on
     * the device receiving it."</p>
     */
    CONSERVE_POWER(5);

    private final int code;

    private DeliveryPriority(final int code) {
        this.code = code;
    }

    protected int getCode() {
        return this.code;
    }

    protected static DeliveryPriority getFromCode(final int code) {
        for (final DeliveryPriority priority : DeliveryPriority.values()) {
            if (priority.getCode() == code) {
                return priority;
            }
        }

        throw new IllegalArgumentException(String.format("No delivery priority found with code %d", code));
    }
}
