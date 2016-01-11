package com.relayrides.pushy.apns.util;

/**
 * A utility class for processing APNs token strings.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class TokenUtil {

    // Prevent instantiation
    private TokenUtil() {}

    /**
     * Returns a "sanitized" version of the given token string suitable for sending to an APNs server. This method
     * returns a version of the original string with all non-hexadecimal digits removed. This can be especially useful
     * when dealing with strings produced with <a href="https://developer.apple.com/library/mac/documentation/Cocoa/Reference/Foundation/Classes/NSData_Class/Reference/Reference.html#//apple_ref/occ/instm/NSData/description">
     * {@code [NSData describe]}</a>.
     *
     * @param tokenString the token string to sanitize
     *
     * @return a "sanitized" version of the given token string suitable for sending to an APNs server
     */
    public static String sanitizeTokenString(final String tokenString) {
        return tokenString.replaceAll("[^a-fA-F0-9]", "");
    }
}
