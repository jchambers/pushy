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

import javax.net.ssl.SSLSession;

/**
 * A factory for push notification handlers that unconditionally accept all push notifications.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see MockApnsServerBuilder#setHandlerFactory(PushNotificationHandlerFactory)
 *
 * @since 0.12
 */
public class AcceptAllPushNotificationHandlerFactory implements PushNotificationHandlerFactory {

    /**
     * Constructs a new push notification handler that unconditionally accepts all push notifications.
     *
     * @param sslSession the SSL session associated with the channel for which this handler will handle notifications
     *
     * @return a new "accept everything" push notification handler
     */
    @Override
    public PushNotificationHandler buildHandler(final SSLSession sslSession) {
        return new PushNotificationHandler() {

            @Override
            public void handlePushNotification(final Http2Headers headers, final ByteBuf payload) {
                // Accept everything unconditionally!
            }
        };
    }
}
