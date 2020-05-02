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

package com.eatthepath.pushy.apns;

import java.time.Instant;
import java.util.Map;

class ErrorResponse {
    private final String reason;
    private final Instant timestamp;

    public ErrorResponse(final String reason, final Instant timestamp) {
        this.reason = reason;
        this.timestamp = timestamp;
    }

    static ErrorResponse fromMap(final Map<String, Object> errorResponseMap) {
        String reason;

        try {
            reason = (String) errorResponseMap.get("reason");
        } catch (final ClassCastException e) {
            reason = null;
        }

        Instant timestamp;

        if (errorResponseMap.containsKey("timestamp")) {
            try {
                timestamp = Instant.ofEpochMilli((Long) errorResponseMap.get("timestamp"));
            } catch (final ClassCastException e) {
                timestamp = null;
            }
        } else {
            timestamp = null;
        }

        return new ErrorResponse(reason, timestamp);
    }

    String getReason() {
        return this.reason;
    }

    Instant getTimestamp() {
        return this.timestamp;
    }
}
