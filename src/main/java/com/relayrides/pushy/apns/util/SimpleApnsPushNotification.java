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

package com.relayrides.pushy.apns.util;

import java.util.Arrays;
import java.util.Date;

import com.relayrides.pushy.apns.ApnsPushNotification;
import com.relayrides.pushy.apns.DeliveryPriority;

/**
 * A simple and immutable implementation of the {@link com.relayrides.pushy.apns.ApnsPushNotification} interface.
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see ApnsPayloadBuilder
 * @see TokenUtil
 */
public class SimpleApnsPushNotification implements ApnsPushNotification {

	private final byte[] token;
	private final String payload;
	private final Date invalidationTime;
	private final DeliveryPriority priority;

	/**
	 * Constructs a new push notification with the given token and payload. No expiration time is set for the
	 * notification, so APNs will not attempt to store the notification for later delivery if the initial attempt fails.
	 * An "immediate" delivery priority is used for the notification, and as such the payload should contain an alert,
	 * sound, or badge component.
	 * 
	 * @param token the device token to which this push notification should be delivered
	 * @param payload the payload to include in this push notification
	 * 
	 * @see DeliveryPriority#IMMEDIATE
	 */
	public SimpleApnsPushNotification(final byte[] token, final String payload) {
		this(token, payload, null, DeliveryPriority.IMMEDIATE);
	}

	/**
	 * Constructs a new push notification with the given token, payload, and expiration time. An "immediate" delivery
	 * priority is used for the notification, and as such the payload should contain an alert, sound, or badge
	 * component.
	 * 
	 * @param token the device token to which this push notification should be delivered
	 * @param payload the payload to include in this push notification
	 * @param invalidationTime the time at which Apple's servers should stop trying to deliver this message; if
	 * {@code null}, no delivery attempts beyond the first will be made
	 * 
	 * @see DeliveryPriority#IMMEDIATE
	 */
	public SimpleApnsPushNotification(final byte[] token, final String payload, final Date invalidationTime) {
		this(token, payload, invalidationTime, DeliveryPriority.IMMEDIATE);
	}

	/**
	 * Constructs a new push notification with the given token, payload, and delivery priority. No expiration time is
	 * set for the notification, so APNs will not attempt to store the notification for later delivery if the initial
	 * attempt fails.
	 *
	 * @param token the device token to which this push notification should be delivered
	 * @param payload the payload to include in this push notification
	 * @param priority the priority with which this notification should be delivered to the receiving device
	 */
	public SimpleApnsPushNotification(final byte[] token, final String payload, final DeliveryPriority priority) {
		this(token, payload, null, priority);
	}

	/**
	 * Constructs a new push notification with the given token, payload, delivery expiration time, and delivery
	 * priority.
	 *
	 * @param token the device token to which this push notification should be delivered
	 * @param payload the payload to include in this push notification
	 * @param invalidationTime the time at which Apple's servers should stop trying to deliver this message; if
	 * {@code null}, no delivery attempts beyond the first will be made
	 * @param priority the priority with which this notification should be delivered to the receiving device
	 */
	public SimpleApnsPushNotification(final byte[] token, final String payload, final Date invalidationTime, final DeliveryPriority priority) {
		this.token = token;
		this.payload = payload;
		this.invalidationTime = invalidationTime;
		this.priority = priority;
	}

	/**
	 * Returns the token of the device to which this push notification should be delivered.
	 * 
	 * @return the token of the device to which this push notification should be delivered
	 */
	public byte[] getToken() {
		return this.token;
	}

	/**
	 * Returns the payload to include in this push notification.
	 * 
	 * @return the payload to include in this push notification
	 */
	public String getPayload() {
		return this.payload;
	}

	/**
	 * Returns the time at which this push notification is no longer valid and should no longer be delivered.
	 * 
	 * @return the time at which this push notification is no longer valid and should no longer be delivered
	 */
	public Date getDeliveryInvalidationTime() {
		return this.invalidationTime;
	}

	/**
	 * Returns the priority with which this push notification should be delivered to the receiving device.
	 * 
	 * @return the priority with which this push notification should be delivered to the receiving device
	 */
	public DeliveryPriority getPriority() {
		return this.priority;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((invalidationTime == null) ? 0 : invalidationTime.hashCode());
		result = prime * result + ((payload == null) ? 0 : payload.hashCode());
		result = prime * result + ((priority == null) ? 0 : priority.hashCode());
		result = prime * result + Arrays.hashCode(token);
		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleApnsPushNotification other = (SimpleApnsPushNotification) obj;
		if (invalidationTime == null) {
			if (other.invalidationTime != null)
				return false;
		} else if (!invalidationTime.equals(other.invalidationTime))
			return false;
		if (payload == null) {
			if (other.payload != null)
				return false;
		} else if (!payload.equals(other.payload))
			return false;
		if (priority != other.priority)
			return false;
		if (!Arrays.equals(token, other.token))
			return false;
		return true;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SimpleApnsPushNotification [token=" + Arrays.toString(token) + ", payload=" + payload
				+ ", invalidationTime=" + invalidationTime + ", priority=" + priority + "]";
	}
}
