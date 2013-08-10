package com.relayrides.pushy.apns.util;

import java.util.Arrays;
import java.util.Date;

import com.relayrides.pushy.apns.ApnsPushNotification;

/**
 * A simple and immutable implementation of the {@code ApnsPushNotification} interface.
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see ApnsPayloadBuilder
 * @see TokenUtil
 */
public class SimpleApnsPushNotification implements ApnsPushNotification {
	
	private final byte[] token;
	private final String payload;
	private final Date expiration;
	
	/**
	 * Constructs a new push notification with the given token and payload. No expiration time is set for the
	 * notification, so APNs will not attempt to store the notification for later delivery if the initial attempt fails.
	 * 
	 * @param token the device token to which this push notification should be delivered
	 * @param payload the payload to include in this push notification
	 */
	public SimpleApnsPushNotification(final byte[] token, final String payload) {
		this(token, payload, null);
	}
	
	/**
	 * Constructs a new push notification with the given token, payload, and delivery expiration time.
	 * 
	 * @param token the device token to which this push notification should be delivered
	 * @param payload the payload to include in this push notification
	 * @param expiration the time at which Apple's servers should stop trying to deliver this message; if {@code null},
	 * no delivery attempts beyond the first will be made
	 */
	public SimpleApnsPushNotification(final byte[] token, final String payload, final Date expiration) {
		this.token = token;
		this.payload = payload;
		this.expiration = expiration;
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
		return this.expiration;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((expiration == null) ? 0 : expiration.hashCode());
		result = prime * result + ((payload == null) ? 0 : payload.hashCode());
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
		if (expiration == null) {
			if (other.expiration != null)
				return false;
		} else if (!expiration.equals(other.expiration))
			return false;
		if (payload == null) {
			if (other.payload != null)
				return false;
		} else if (!payload.equals(other.payload))
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
		return "SimpleApnsPushNotification [token=" + TokenUtil.tokenBytesToString(token)
				+ ", payload=" + payload + ", expiration=" + expiration + "]";
	}
}
