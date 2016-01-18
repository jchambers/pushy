package com.relayrides.pushy.apns.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TokenUtilTest {

    @Test
    public void testSanitizeTokenString() {
        assertEquals("ffff1234", TokenUtil.sanitizeTokenString("<ffff 1234>"));
    }

    @Test(expected = NullPointerException.class)
    public void testSanitizeNullTokenString() {
        TokenUtil.sanitizeTokenString(null);
    }
}
