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

package com.turo.pushy.apns.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Converts {@link java.util.Date} instances in JSON objects to or from timestamps since the epoch.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public class DateAsTimeSinceEpochTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date> {

    private final TimeUnit timeUnit;

    /**
     * Constructs a new date/timestamp type adapter that uses the given time unit to measure time since the epoch.
     *
     * @param timeUnit the time unit in which to express timestamps
     */
    public DateAsTimeSinceEpochTypeAdapter(final TimeUnit timeUnit) {
        Objects.requireNonNull(timeUnit);
        this.timeUnit = timeUnit;
    }

    @Override
    public Date deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context) throws JsonParseException {
        final Date date;

        if (json.isJsonPrimitive()) {
            date = new Date(this.timeUnit.toMillis(json.getAsLong()));
        } else if (json.isJsonNull()) {
            date = null;
        } else {
            throw new JsonParseException("Dates represented as time since the epoch must either be numbers or null.");
        }

        return date;
    }

    @Override
    public JsonElement serialize(final Date src, final Type typeOfSrc, final JsonSerializationContext context) {
        final JsonElement element;

        if (src != null) {
            element = new JsonPrimitive(this.timeUnit.convert(src.getTime(), TimeUnit.MILLISECONDS));
        } else {
            element = JsonNull.INSTANCE;
        }

        return element;
    }
}
