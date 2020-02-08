/*
 * Copyright (c) 2020 Jon Chambers
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

package com.turo.pushy.apns.util;

/**
 * A utility class for processing APNs token strings.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class TokenUtil {

    private TokenUtil() {
        // A private constructor prevents callers from accidentally constucting a TokenUtil instance
    }

    /**
     * Returns a "sanitized" version of the given token string suitable for sending to an APNs server. This method
     * returns a version of the original string with all non-hexadecimal digits removed. This can be especially useful
     * when dealing with strings produced with <a href="https://developer.apple.com/documentation/foundation/nsdata/1412579-description">
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
