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
