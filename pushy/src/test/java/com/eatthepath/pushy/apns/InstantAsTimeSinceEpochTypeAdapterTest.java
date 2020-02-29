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

import com.eatthepath.pushy.apns.util.InstantAsTimeSinceEpochTypeAdapter;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InstantAsTimeSinceEpochTypeAdapterTest {

    @Test(expected = NullPointerException.class)
    public void testInstantAsTimeSinceEpochTypeAdapterNullUnits() {
        new InstantAsTimeSinceEpochTypeAdapter(null);
    }

    @Test
    public void testDeserialize() {
        {
            final InstantAsTimeSinceEpochTypeAdapter adapter = new InstantAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);
            assertNull(adapter.deserialize(JsonNull.INSTANCE, Instant.class, null));
        }

        {
            final InstantAsTimeSinceEpochTypeAdapter adapter = new InstantAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);

            final long timestampInMilliseconds = Instant.now().toEpochMilli();
            final Instant instantFromTimestamp = Instant.ofEpochMilli(timestampInMilliseconds);

            assertEquals(instantFromTimestamp, adapter.deserialize(new JsonPrimitive(timestampInMilliseconds), Instant.class, null));
        }

        {
            final InstantAsTimeSinceEpochTypeAdapter adapter = new InstantAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS);

            final long timestampInSeconds = Instant.now().getEpochSecond();
            final Instant instantFromTimestamp = Instant.ofEpochSecond(timestampInSeconds);

            assertEquals(instantFromTimestamp, adapter.deserialize(new JsonPrimitive(timestampInSeconds), Instant.class, null));
        }

    }

    @Test
    public void testSerialize() {
        {
            final InstantAsTimeSinceEpochTypeAdapter adapter = new InstantAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);
            assertEquals(JsonNull.INSTANCE, adapter.serialize(null, Instant.class, null));
        }

        {
            final InstantAsTimeSinceEpochTypeAdapter adapter = new InstantAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS);

            final long timestampInMilliseconds = Instant.now().toEpochMilli();
            final Instant instantFromTimestamp = Instant.ofEpochMilli(timestampInMilliseconds);

            assertEquals(new JsonPrimitive(timestampInMilliseconds), adapter.serialize(instantFromTimestamp, Instant.class, null));
        }

        {
            final InstantAsTimeSinceEpochTypeAdapter adapter = new InstantAsTimeSinceEpochTypeAdapter(TimeUnit.SECONDS);

            final long timestampInSeconds = Instant.now().getEpochSecond();
            final Instant instantFromTimestamp = Instant.ofEpochSecond(timestampInSeconds);

            assertEquals(new JsonPrimitive(timestampInSeconds), adapter.serialize(instantFromTimestamp, Instant.class, null));
        }
    }
}
