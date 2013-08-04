package com.relayrides.pushy;

public enum ApnsErrorCode {
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
	 * <p>According to Apple's documentation:</p>
	 * 
	 * <blockquote>A status code of 10 indicates that the APNs server closed the
	 * connection (for example, to perform maintenance). The notification
	 * identifier in the error response indicates the last notification that was
	 * successfully sent. Any notifications you sent after it have been
	 * discarded and must be resent. When you receive this status code, stop
	 * using this connection and open a new connection.</blockquote>
	 */
	SHUTDOWN((byte)10),
	
	UNKNOWN((byte)255);
	
	private final byte code;
	
	private ApnsErrorCode(final byte code) {
		this.code = code;
	}
	
	public byte getCode() {
		return this.code;
	}
	
	public static ApnsErrorCode getByCode(final byte code) {
		for (final ApnsErrorCode error : ApnsErrorCode.values()) {
			if (error.code == code) {
				return error;
			}
		}
		
		throw new IllegalArgumentException(String.format("Unrecognized error code: %d", code));
	}
}
