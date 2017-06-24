/*
 * Copyright (c) 2013-2017 Turo
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

package com.turo.pushy.apns;

/**
 * An exception that indicates that a push notification could not be sent due to an upstream server error. Server errors
 * should be considered temporary failures, and callers should attempt to send the notification again later.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 */
public class ApnsServerException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new APNs server exception.
     */
    public ApnsServerException() {
        super();
    }

    /**
     * Constructs a new APNs server exception with the given message.
     *
     * @param message a message from the server (if any) explaining the error; may be {@code null}
     */
    public ApnsServerException(final String message) {
        super(message);
    }
}
