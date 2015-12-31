package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

public class DateAsSecondsSinceEpochTypeAdapterTest {

    @Test
    public void testDeserialize() {
        final DateAsSecondsSinceEpochTypeAdapter adapter = new DateAsSecondsSinceEpochTypeAdapter();

        assertNull(adapter.deserialize(JsonNull.INSTANCE, Date.class, null));

        {
            final long timestampInSeconds = (System.currentTimeMillis() / 1000);
            final Date dateFromTimestamp = new Date(timestampInSeconds * 1000);

            assertEquals(dateFromTimestamp, adapter.deserialize(new JsonPrimitive(timestampInSeconds), Date.class, null));
        }
    }

    @Test
    public void testSerialize() {
        final DateAsSecondsSinceEpochTypeAdapter adapter = new DateAsSecondsSinceEpochTypeAdapter();

        assertEquals(JsonNull.INSTANCE, adapter.serialize(null, Date.class, null));

        {
            final long timestampInSeconds = (System.currentTimeMillis() / 1000);
            final Date dateFromTimestamp = new Date(timestampInSeconds * 1000);

            assertEquals(new JsonPrimitive(timestampInSeconds), adapter.serialize(dateFromTimestamp, Date.class, null));
        }
    }
}
