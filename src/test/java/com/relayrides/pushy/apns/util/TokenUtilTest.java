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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TokenUtilTest {

	@Test
	public void testTokenStringToByteArray() throws MalformedTokenStringException {
		final String tokenString = "<740f4707 61bb78ad>";
		final byte[] expectedTokenBytes = new byte[] { 0x74, 0x0f, 0x47, 0x07, 0x61, (byte) 0xbb, 0x78, (byte) 0xad };

		assertArrayEquals(expectedTokenBytes, TokenUtil.tokenStringToByteArray(tokenString));
	}

	@Test(expected = MalformedTokenStringException.class)
	public void testTokenStringToByteArrayOddStringLength() throws MalformedTokenStringException {
		final String tokenString = "<740f4707 61bb78a>";
		TokenUtil.tokenStringToByteArray(tokenString);
	}

	@Test(expected = NullPointerException.class)
	public void testTokenStringToByteArrayNullString() throws MalformedTokenStringException {
		TokenUtil.tokenStringToByteArray(null);
	}

	@Test
	public void testTokenBytesToString() {
		final byte[] tokenBytes = { 0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef };
		final String expectedTokenString = "0123456789abcdef";

		assertTrue(expectedTokenString.equalsIgnoreCase(TokenUtil.tokenBytesToString(tokenBytes)));
	}

	@Test(expected = NullPointerException.class)
	public void testTokenBytesToStringNullBytes() {
		TokenUtil.tokenBytesToString(null);
	}
}
