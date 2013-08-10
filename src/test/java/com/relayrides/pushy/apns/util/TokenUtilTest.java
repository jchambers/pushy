package com.relayrides.pushy.apns.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.relayrides.pushy.apns.util.TokenUtil;

public class TokenUtilTest {

	@Test
	public void testTokenStringToByteArray() {
		final String tokenString = "<740f4707 61bb78ad>";
		final byte[] expectedTokenBytes = new byte[] { 0x74, 0x0f, 0x47, 0x07, 0x61, (byte) 0xbb, 0x78, (byte) 0xad };
		
		assertArrayEquals(expectedTokenBytes, TokenUtil.tokenStringToByteArray(tokenString));
	}

	@Test
	public void testTokenBytesToString() {
		final byte[] tokenBytes = { 0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef };
		final String expectedTokenString = "0123456789abcdef";
		
		assertTrue(expectedTokenString.equalsIgnoreCase(TokenUtil.tokenBytesToString(tokenBytes)));
	}

}
