/* Copyright (c) 2014 RelayRides
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
 * An enumeration of frame items that may be included in an APNs push notification.
 * 
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
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
