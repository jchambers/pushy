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

import javax.net.ssl.SSLSession;

/**
 * A push notification handler factory constructs {@link PushNotificationHandler} instances when a mock APNs server
 * accepts a new connection. Handlers created by the factory control how the server responds to the push notifications
 * it receives.
 *
 * @since 0.12
 */
public interface PushNotificationHandlerFactory {
    /**
     * Constructs a new push notification handler that will process notifications from a single connection to a mock
     * server.
     *
     * @param sslSession the SSL session for the new connection to the mock server
     *
     * @return a new push notification handler for the new connection
     */
    PushNotificationHandler buildHandler(SSLSession sslSession);
}
