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

/**
 * <p>A utility class for converting APNS device tokens between byte arrays and hexadecimal strings.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class TokenUtil {
	private static final String NON_HEX_CHARACTER_PATTERN = "[^0-9a-fA-F]";

	private TokenUtil() {}

	/**
	 * <p>Converts a string of hexadecimal characters into a byte array. All non-hexadecimal characters (i.e. 0-9 and
	 * A-F) are ignored in the conversion. Note that this means that
	 * {@code TokenUtil.tokenBytesToString(TokenUtil.tokenStringToByteArray(tokenString))} may be different from
	 * {@code tokenString}.</p>
	 *
	 * <p>As an example, a valid token string may look something like this: {@code <740f4707 61bb78ad>}. This method
	 * would return a byte array with the following values: {@code [0x74 0x0f 0x47 0x07 0x61 0xbb 0x78 0xad]}.</p>
	 *
	 * @param tokenString a string of hexadecimal characters to interpret as a byte array
	 *
	 * @return a byte array containing the values represented in the token string
	 *
	 * @throws MalformedTokenStringException if the given token string could not be parsed as an APNs token
	 * @throws NullPointerException if the given string is {@code null}
	 */
	public static byte[] tokenStringToByteArray(final String tokenString) throws MalformedTokenStringException {

		if (tokenString == null) {
			throw new NullPointerException("Token string must not be null.");
		}

		final String strippedTokenString = tokenString.replaceAll(NON_HEX_CHARACTER_PATTERN, "");

		if (strippedTokenString.length() % 2 != 0) {
			throw new MalformedTokenStringException("Token strings must contain an even number of hexadecimal digits.");
		}

		final byte[] tokenBytes = new byte[strippedTokenString.length() / 2];

		for (int i = 0; i < strippedTokenString.length(); i += 2) {
			tokenBytes[i / 2] = (byte) (Integer.parseInt(strippedTokenString.substring(i, i + 2), 16));
		}

		return tokenBytes;
	}

	/**
	 * <p>Converts an array of bytes into a string of hexadecimal characters representing the values in the array. For
	 * example, calling this method on a byte array with the values {@code [0x01 0x23 0x45 0x67 0x89 0xab]} would return
	 * {@code "0123456789ab"}. No guarantees are made as to the case of the returned string.</p>
	 *
	 * @param tokenBytes an array of bytes to represent as a string
	 *
	 * @return a string of hexadecimal characters representing the values in the given byte array
	 *
	 * @throws NullPointerException if the given byte array is {@code null}
	 */
	public static String tokenBytesToString(final byte[] tokenBytes) {
		if (tokenBytes == null) {
			throw new NullPointerException("Token byte array must not be null.");
		}

		final StringBuilder builder = new StringBuilder();

		for (final byte b : tokenBytes) {
			final String hexString = Integer.toHexString(b & 0xff);

			if (hexString.length() == 1) {
				// We need a leading zero
				builder.append("0");
			}

			builder.append(hexString);
		}

		return builder.toString();
	}
}
