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

import java.util.Arrays;
import java.util.Date;

import com.relayrides.pushy.apns.util.TokenUtil;

/**
 * <p>Represents a device token that the APN Feedback Service has reported as expired. According to Apple's
 * documentation:</p>
 * 
 * <blockquote>When a push notification cannot be delivered because the intended app does not exist on the device, the
 * feedback service adds that device's token to its list. Push notifications that expire before being delivered are
 * not considered a failed delivery and don't impact the feedback service.</blockquote>
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 * 
 * @see <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/CommunicatingWIthAPS.html#//apple_ref/doc/uid/TP40008194-CH101-SW3">
 * Local and Push Notification Programming Guide - Provider Communication with Apple Push Notification Service - The
 * Feedback Service</a>
 */
public class ExpiredToken {
	private final byte[] token;
	private final Date expiration;

	protected ExpiredToken(final byte[] token, final Date expiration) {
		this.token = java.util.Arrays.copyOf(token, token.length);
		this.expiration = new Date(expiration.getTime());
	}

	/**
	 * Returns the token APNs has reported as expired.
	 * 
	 * @return the expired token
	 */
	public byte[] getToken() {
		return this.token;
	}

	/**
	 * Returns the time, rounded to the nearest second, when APNs determined that the application no longer exists on
	 * the device.
	 * 
	 * @return the time, rounded to the nearest second, when APNs determined that the application no longer exists on
	 * the device
	 */
	public Date getExpiration() {
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
		ExpiredToken other = (ExpiredToken) obj;
		if (expiration == null) {
			if (other.expiration != null)
				return false;
		} else if (!expiration.equals(other.expiration))
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
		return "ExpiredToken [token=" + TokenUtil.tokenBytesToString(token)
				+ ", expiration=" + expiration + "]";
	}
}
