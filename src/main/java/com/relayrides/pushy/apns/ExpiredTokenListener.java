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

import java.util.Collection;

/**
 * <p>Listens for expired tokens from the APNs feedback service.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see PushManager#registerExpiredTokenListener(ExpiredTokenListener)
 * @see PushManager#unregisterExpiredTokenListener(ExpiredTokenListener)
 * @see PushManager#requestExpiredTokens()
 */
public interface ExpiredTokenListener<T extends ApnsPushNotification> {

	/**
	 * Handles a collection of expired tokens received from the APNs feedback service.
	 *
	 * @param pushManager the push manager that reported the expired tokens
	 * @param expiredTokens a collection of tokens that have expired since the last call to the feedback service
	 */
	void handleExpiredTokens(PushManager<? extends T> pushManager, Collection<ExpiredToken> expiredTokens);
}
