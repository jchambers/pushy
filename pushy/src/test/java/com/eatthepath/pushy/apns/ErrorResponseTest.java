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

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ErrorResponseTest {

    @Test
    void fromMap() {
        {
            final ErrorResponse emptyError = ErrorResponse.fromMap(Collections.emptyMap());

            assertNull(emptyError.getReason());
            assertNull(emptyError.getTimestamp());
        }

        {
            final String reason = "Badness";

            final ErrorResponse reasonOnlyError =
                    ErrorResponse.fromMap(Collections.singletonMap("reason", reason));

            assertEquals(reason, reasonOnlyError.getReason());
            assertNull(reasonOnlyError.getTimestamp());
        }

        {
            final String reason = "Badness";
            final Instant timestamp = Instant.now();

            final Map<String, Object> errorResponseMap = new HashMap<>();
            errorResponseMap.put("reason", reason);
            errorResponseMap.put("timestamp", timestamp.toEpochMilli());

            final ErrorResponse errorResponse = ErrorResponse.fromMap(errorResponseMap);

            assertEquals(reason, errorResponse.getReason());
            assertEquals(timestamp, errorResponse.getTimestamp());
        }

        {
            final Map<String, Object> wrongTypesErrorMap = new HashMap<>();
            wrongTypesErrorMap.put("reason", 17);
            wrongTypesErrorMap.put("timestamp", false);

            final ErrorResponse errorResponse = ErrorResponse.fromMap(wrongTypesErrorMap);

            assertNull(errorResponse.getReason());
            assertNull(errorResponse.getTimestamp());
        }
    }
}
