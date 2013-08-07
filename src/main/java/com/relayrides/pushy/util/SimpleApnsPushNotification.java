package com.relayrides.pushy.util;

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
}
