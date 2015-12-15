package com.relayrides.pushy.apns;

import java.util.Random;

public class TokenTestUtil {
    private static final int TOKEN_LENGTH = 32;

    public static String generateRandomToken() {
        final byte[] tokenBytes = new byte[TOKEN_LENGTH];
        new Random().nextBytes(tokenBytes);

        final StringBuilder builder = new StringBuilder(TOKEN_LENGTH * 2);

        for (final byte b : tokenBytes) {
            final String hexString = Integer.toHexString(b & 0xff);

            if (hexString.length() == 1) {
                // We need a leading zero
                builder.append('0');
            }

            builder.append(hexString);
        }

        return builder.toString();
    }
}
