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

/**
 * A {@code FeedbackServiceListener} receives lifecycle events from {@link FeedbackConnection} instances. Handler
 * methods are called from IO threads in the connection's event loop, and as such handler method implementations
 * <em>must not</em> perform blocking operations. Blocking operations should be dispatched to separate threads.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
interface FeedbackConnectionListener extends ApnsConnectionListener {

	/**
	 * Indicates that the given connection received an expired token from the APNs feedback service.
	 *
	 * @param connection the connection that received the expired token
	 * @param token the expired token
	 */
	void handleExpiredToken(FeedbackConnection connection, ExpiredToken token);
}
