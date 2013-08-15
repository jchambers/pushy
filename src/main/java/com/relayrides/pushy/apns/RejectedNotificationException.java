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

/**
 * <p>Indicates that a notification was definitively rejected by APNs for a specific reason.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class RejectedNotificationException extends Exception {
	private static final long serialVersionUID = 1L;
	
	private final int sequenceNumber;
	private final RejectedNotificationReason errorCode;
	
	/**
	 * Constructs a new rejected notification exception indicating that the notification sent with the given sequence
	 * number was rejected for the given reason.
	 * 
	 * @param sequenceNumber the sequence number of the rejected notification
	 * @param errorCode the reason reported by APNs for the rejection
	 */
	public RejectedNotificationException(final int sequenceNumber, final RejectedNotificationReason errorCode) {
		this.sequenceNumber = sequenceNumber;
		this.errorCode = errorCode;
	}
	
	/**
	 * Returns the sequence number of the notification rejected by APNs.
	 * 
	 * @return the sequence number of the notification rejected by APNs
	 */
	public int getSequenceNumber() {
		return this.sequenceNumber;
	}
	
	/**
	 * Returns the reason the notification was rejected by APNs.
	 * 
	 * @return the reason the notification was rejected by APNs
	 */
	public RejectedNotificationReason getReason() {
		return this.errorCode;
	}
}
