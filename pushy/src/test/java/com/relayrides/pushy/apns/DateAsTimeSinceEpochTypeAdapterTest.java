package com.relayrides.pushy.apns;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Date;
import java.util.concurrent.TimeUnit;

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
