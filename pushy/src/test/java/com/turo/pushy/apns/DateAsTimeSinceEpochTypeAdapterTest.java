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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import com.turo.pushy.apns.util.DateAsTimeSinceEpochTypeAdapter;
import org.junit.Test;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

public class DateAsTimeSinceEpochTypeAdapterTest {

    @Test(expected = NullPointerException.class)
    public void testDateAsTimeSinceEpochTypeAdapterNullUnits() {
        new DateAsTimeSinceEpochTypeAdapter(null);
    }

    @Test
    public void testDeserialize() {
        {
            final DateAsTimeSinceEpochTypeAdapter adapter = new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);
            assertNull(adapter.deserialize(JsonNull.INSTANCE, Date.class, null));
        }

        {
            final DateAsTimeSinceEpochTypeAdapter adapter = new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);

            final long timestampInMilliseconds = System.currentTimeMillis();
            final Date dateFromTimestamp = new Date(timestampInMilliseconds);

            assertEquals(dateFromTimestamp, adapter.deserialize(new JsonPrimitive(timestampInMilliseconds), Date.class, null));
        }

        {
            final DateAsTimeSinceEpochTypeAdapter adapter = new DateAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS);

            final long timestampInSeconds = System.currentTimeMillis() / 1000;
            final Date dateFromTimestamp = new Date(timestampInSeconds * 1000);

            assertEquals(dateFromTimestamp, adapter.deserialize(new JsonPrimitive(timestampInSeconds), Date.class, null));
        }

    }

    @Test
    public void testSerialize() {
        {
            final DateAsTimeSinceEpochTypeAdapter adapter = new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);
            assertEquals(JsonNull.INSTANCE, adapter.serialize(null, Date.class, null));
        }

        {
            final DateAsTimeSinceEpochTypeAdapter adapter = new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);

            final long timestampInMilliseconds = System.currentTimeMillis();
            final Date dateFromTimestamp = new Date(timestampInMilliseconds);

            assertEquals(new JsonPrimitive(timestampInMilliseconds), adapter.serialize(dateFromTimestamp, Date.class, null));
        }

        {
            final DateAsTimeSinceEpochTypeAdapter adapter = new DateAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS);

            final long timestampInSeconds = System.currentTimeMillis() / 1000;
            final Date dateFromTimestamp = new Date(timestampInSeconds * 1000);

            assertEquals(new JsonPrimitive(timestampInSeconds), adapter.serialize(dateFromTimestamp, Date.class, null));
        }
    }
}
