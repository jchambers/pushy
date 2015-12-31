package com.relayrides.pushy.apns;

import java.lang.reflect.Type;
import java.util.Date;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

class DateAsSecondsSinceEpochTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {

    @Override
    public Date deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context) throws JsonParseException {
        final Date date;

        if (json.isJsonPrimitive()) {
            date = new Date(json.getAsLong() * 1000);
        } else if (json.isJsonNull()) {
            date = null;
        } else {
            throw new JsonParseException("Dates represented as seconds since the epoch must either be numbers or null.");
        }

        return date;
    }

    @Override
    public JsonElement serialize(final Date src, final Type typeOfSrc, final JsonSerializationContext context) {
        final JsonElement element;

        if (src != null) {
            element = new JsonPrimitive(src.getTime() / 1000);
        } else {
            element = JsonNull.INSTANCE;
        }

        return element;
    }
}
