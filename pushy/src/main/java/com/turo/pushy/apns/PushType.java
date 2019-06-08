/*
 * Copyright (c) 2013-2019 Turo
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

/**
 * An enumeration of push notification display types. Note that push notification display types are required in iOS 13
 * and later and watchOS 6 and later, but are ignored under earlier versions of either operating system.
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/sending_notification_requests_to_apns">Sending
 * Notification Requests to APNs</a>
 *
 * @since 0.13.9
 */
public enum PushType {

    /**
     * Indicates that a push notification is expected to display an alert, play a sound, or badges the receiving apps'
     * icon.
     */
    ALERT("alert"),

    /**
     * Indicates that a push notification is not expected to interact with the user on the receiving device.
     */
    BACKGROUND("background");

    private final String headerValue;

    PushType(final String headerValue) {
        this.headerValue = headerValue;
    }

    public String getHeaderValue() {
        return this.headerValue;
    }

    public static PushType getFromHeaderValue(final CharSequence headerValue) {
        for (final PushType pushType : PushType.values()) {
            if (pushType.headerValue.contentEquals(headerValue)) {
                return pushType;
            }
        }

        throw new IllegalArgumentException("No push type found for header value: " + headerValue);
    }
}
