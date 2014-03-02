/* Copyright (c) 2013 RelayRides
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

import java.util.Date;

/**
 * <p>A deliberately-malformed push notification used to trigger a remote shutdown of an APNs connection.</p>
 * 
 * <p>The APNs gateway does not acknowledge successful push notifications, and so it is impossible to know for sure
 * when a connection can be safely closed from the client side, or the state of messages sent before the connection
 * was closed. Because the APNs gateway DOES reject bad notifications, though, we can send a known-bad notification to
 * the gateway and wait for the remote host to close the connection. In that case, we know that all notifications sent
 * before the known-bad notification have been processed and that all messages after the known-bad notification were
 * not.</p>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class KnownBadPushNotification implements ApnsPushNotification {

	public byte[] getToken() {
		return new byte[0];
	}

	public String getPayload() {
		return "";
	}

	public Date getDeliveryInvalidationTime() {
		return null;
	}

	public DeliveryPriority getPriority() {
		return DeliveryPriority.IMMEDIATE;
	}
}
