package com.relayrides.pushy.apns;

enum ApnsFrameItem {
	DEVICE_TOKEN((byte)1),
	PAYLOAD((byte)2),
	SEQUENCE_NUMBER((byte)3),
	EXPIRATION((byte)4),
	PRIORITY((byte)5);

	private final byte code;

	private ApnsFrameItem(final byte code) {
		this.code = code;
	}

	protected byte getCode() {
		return this.code;
	}

	protected static ApnsFrameItem getFrameItemFromCode(final byte code) {
		for (final ApnsFrameItem item : ApnsFrameItem.values()) {
			if (item.getCode() == code) {
				return item;
			}
		}

		throw new IllegalArgumentException(String.format("No frame item found with code %d", code));
	}
}
