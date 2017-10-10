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

package com.turo.pushy.apns.server;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2Headers;

import java.util.Date;

/**
 * <p>Mock APNs server listeners are notified when push notifications are accepted or rejected by a
 * {@link MockApnsServer}. Listeners have no effect on a server's handling of push notifications, and are designed for
 * use in integration testing.</p>
 *
 * <p>Note that the mock server's decision to accept or reject a push notification is controlled by its
 * {@link PushNotificationHandler}. As a result, notifications accepted by a mock server might be rejected by a real
 * APNs server or vice versa. Notifications passed to listeners may have missing, incomplete, or nonsense data
 * regardless of whether they were accepted or rejected, and callers should use appropriate caution. Parsing
 * notification headers/payloads into {@link com.turo.pushy.apns.ApnsPushNotification} instances is the responsibility
 * of the caller, and callers may with to subclass {@link ParsingMockApnsServerListenerAdapter} if they need to work
 * with {@code ApnsPushNotification} instances instead of raw headers and byte buffers.</p>
 *
 * @see MockApnsServerBuilder#setListener(MockApnsServerListener)
 * @see ParsingMockApnsServerListenerAdapter
 *
 * @since 0.12
 */
public interface MockApnsServerListener {

    /**
     * Indicates that a push notification has been accepted by the mock server.
     *
     * @param headers the notification's HTTP/2 headers
     * @param payload the notification's payload
     */
    void handlePushNotificationAccepted(Http2Headers headers, ByteBuf payload);

    /**
     * Indicates that a push notification has been rejected by the mock server.
     *
     * @param headers the notification's HTTP/2 headers
     * @param payload the notification's payload
     * @param rejectionReason the reason the push notification was rejected by the mock server
     * @param deviceTokenExpirationTimestamp the time at which the push notification's destination device token expired;
     * may be {@code null} if the token has not expired
     */
    void handlePushNotificationRejected(Http2Headers headers, ByteBuf payload, RejectionReason rejectionReason, Date deviceTokenExpirationTimestamp);
}
