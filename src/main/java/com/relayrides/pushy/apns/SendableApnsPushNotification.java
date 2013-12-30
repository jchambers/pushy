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

import com.relayrides.pushy.apns.util.TokenUtil;

/**
 * <p>Represents a push notification wrapped with transmission-related metadata ready to be sent to an APNs server.
 * Sendable push notifications include a sequence number that can be used to identify notifications rejected by the
 * APNs server.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class SendableApnsPushNotification<T extends ApnsPushNotification> {
	private final T pushNotification;
	private final int sequenceNumber;

	/**
	 * Constructs a sendable push notification with the given base notification and sequence number.
	 * 
	 * @param pushNotification the underlying push notification
	 * @param sequenceNumber the channel-specific sequence number with which to send this notification
	 */
	public SendableApnsPushNotification(final T pushNotification, final int sequenceNumber) {
		this.pushNotification = pushNotification;
		this.sequenceNumber = sequenceNumber;
	}

	/**
	 * Returns the push notification to be sent.
	 * 
	 * @return the push notification to be sent
	 */
	public T getPushNotification() {
		return this.pushNotification;
	}

	/**
	 * Returns the channel-specific sequence number for this push notification.
	 * 
	 * @return the channel-specific sequence number for this push notification
	 */
	public int getSequenceNumber() {
		return this.sequenceNumber;
	}

	@Override
	public String toString() {
		return String.format("SendableApnsPushNotification [sequenceNumber=%d, token=%s, payload=%s, deliveryInvalidation=%s]",
				this.sequenceNumber, TokenUtil.tokenBytesToString(this.pushNotification.getToken()),
				this.pushNotification.getPayload(), this.pushNotification.getDeliveryInvalidationTime());
	}
}
