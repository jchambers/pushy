/* Copyright (c) 2014 RelayRides
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

package com.relayrides.pushy.apns;

import javax.net.ssl.SSLHandshakeException;

/**
 * <p>Listens for failed attempts to connect to an APNs gateway. Generally, a push manager will continue to try to
 * connect until it is shut down (under the assumption that failures are temporary).</p>
 *
 * <p>While some connection failures are temporary and likely to be resolved by retrying the connection, some causes of
 * failure are more permanent; generally, an {@link SSLHandshakeException} indicates a problem with SSL certificates
 * that is unlikely to be resolved by retrying the connection. Applications using Pushy are encouraged to register a
 * listener that watches for handshake exceptions that shuts down a push manager in the event of a pattern of failure
 * (transient connection problems can cause spurious exceptions, so it's best not to shut down after a single
 * exception).</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see com.relayrides.pushy.apns.PushManager#registerFailedConnectionListener(FailedConnectionListener)
 * @see com.relayrides.pushy.apns.PushManager#unregisterFailedConnectionListener(FailedConnectionListener)
 */
public interface FailedConnectionListener<T extends ApnsPushNotification> {

	/**
	 * Handles a failed attempt to connect to the APNs gateway.
	 *
	 * @param pushManager the push manager that failed to open a connection
	 * @param cause the cause for the connection failure
	 */
	void handleFailedConnection(PushManager<? extends T> pushManager, Throwable cause);
}
