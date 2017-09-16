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

/**
 * <p>Push notification handlers process push notifications sent to a {@link MockApnsServer} and decide how the server
 * should respond to those notifications. Implementers can write handlers to simulate a variety of server behaviors and
 * error conditions without setting up elaborate pre-conditions.</p>
 *
 * <p>Push notification handler instances are bound to a specific connection and will always be called by the same
 * thread; as a result, implementations are inherently thread-safe.</p>
 *
 * @see PushNotificationHandlerFactory
 *
 * @since 0.12
 */
public interface PushNotificationHandler {

    /**
     * Processes a push notification received by a mock server. Implementations should exit normally to indicate that a
     * notification should be accepted by the server. If an implementation throws a
     * {@link RejectedNotificationException}, the server will reject the notification. If implementations throw a
     * {@link RuntimeException}, the server will report an internal server error to clients.
     *
     * @param headers the notification's HTTP/2 headers
     * @param payload the notification's payload; may be empty or {@code null}
     *
     * @throws RejectedNotificationException if the server should reject the push notification for a specific reason
     */
    void handlePushNotification(Http2Headers headers, ByteBuf payload) throws RejectedNotificationException;
}
