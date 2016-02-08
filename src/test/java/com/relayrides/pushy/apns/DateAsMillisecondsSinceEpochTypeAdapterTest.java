package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

public class DateAsMillisecondsSinceEpochTypeAdapterTest {

    @Test
    public void testDeserialize() {
        final DateAsMillisecondsSinceEpochTypeAdapter adapter = new DateAsMillisecondsSinceEpochTypeAdapter();

        assertNull(adapter.deserialize(JsonNull.INSTANCE, Date.class, null));

        {
            final long timestampInSeconds = System.currentTimeMillis();
            final Date dateFromTimestamp = new Date(timestampInSeconds);

            assertEquals(dateFromTimestamp, adapter.deserialize(new JsonPrimitive(timestampInSeconds), Date.class, null));
        }
    }

    @Test
    public void testSerialize() {
        final DateAsMillisecondsSinceEpochTypeAdapter adapter = new DateAsMillisecondsSinceEpochTypeAdapter();

        assertEquals(JsonNull.INSTANCE, adapter.serialize(null, Date.class, null));

        {
            final long timestampInSeconds = System.currentTimeMillis();
            final Date dateFromTimestamp = new Date(timestampInSeconds);

            assertEquals(new JsonPrimitive(timestampInSeconds), adapter.serialize(dateFromTimestamp, Date.class, null));
        }
    }
}
