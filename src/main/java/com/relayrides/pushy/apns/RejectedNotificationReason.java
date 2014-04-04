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
 * An enumeration of error codes that may be returned by APNs to indicate why a push notification was rejected. With
 * the exception of {@code SHUTDOWN}, all rejections are assumed to be permanent failures.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public enum RejectedNotificationReason {
	NO_ERROR((byte)0),
	PROCESSING_ERROR((byte)1),
	MISSING_TOKEN((byte)2),
	MISSING_TOPIC((byte)3),
	MISSING_PAYLOAD((byte)4),
	INVALID_TOKEN_SIZE((byte)5),
	INVALID_TOPIC_SIZE((byte)6),
	INVALID_PAYLOAD_SIZE((byte)7),
	INVALID_TOKEN((byte)8),

	/**
	 * <p>Indicates that the notification was accepted, but the connection is being shut down for maintenance.
	 * According to Apple's documentation:</p>
	 * 
	 * <blockquote>A status code of 10 indicates that the APNs server closed the connection (for example, to perform
	 * maintenance). The notification identifier in the error response indicates the last notification that was
	 * successfully sent. Any notifications you sent after it have been discarded and must be resent. When you receive
	 * this status code, stop using this connection and open a new connection.</blockquote>
	 */
	SHUTDOWN((byte)10),

	UNKNOWN((byte)255);

	private final byte errorCode;

	private RejectedNotificationReason(final byte errorCode) {
		this.errorCode = errorCode;
	}

	/**
	 * Returns the one-byte error code associated with this rejection reason.
	 * 
	 * @return the one-byte error code associated with this rejection reason
	 */
	public byte getErrorCode() {
		return this.errorCode;
	}

	/**
	 * Gets the rejection reason associated with the given error code.
	 * 
	 * @param errorCode the error code for which to retrieve a rejection reason
	 * 
	 * @return the rejection reason associated with {@code errorCode}
	 */
	public static RejectedNotificationReason getByErrorCode(final byte errorCode) {
		for (final RejectedNotificationReason error : RejectedNotificationReason.values()) {
			if (error.errorCode == errorCode) {
				return error;
			}
		}

		throw new IllegalArgumentException(String.format("Unrecognized error code: %d", errorCode));
	}
}
