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

package com.eatthepath.pushy.apns.util;

import com.eatthepath.json.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public abstract class LiveActivityApnsPayloadBuilderTest {

    private LiveActivityApnsPayloadBuilder builder;

    protected abstract LiveActivityApnsPayloadBuilder getBuilder();

    @BeforeEach
    public void setUp() {
        this.builder = getBuilder();
    }

    @Test
    void testSetAlertBody() {
        final String alertBody = "This is a test alert message.";

        this.builder.setAlertBody(alertBody);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertBody, alert.get("body"));
        }
    }

    @Test
    void testSetAlertTitle() {
        final String alertTitle = "This is a test alert message.";

        this.builder.setAlertTitle(alertTitle);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertTitle, alert.get("title"));
        }
    }

    @Test
    void testSetAlertTitleAndBody() {
        final String alertTitle = "This is a short alert title";
        final String alertBody = "This is a longer alert body";

        this.builder.setAlertBody(alertBody);
        this.builder.setAlertTitle(alertTitle);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertTitle, alert.get("title"));
            assertEquals(alertBody, alert.get("body"));
        }
    }

    @Test
    void testSetAlertSound() {
        final String soundFileName = "dying-giraffe.aiff";
        this.builder.setAlertSound(soundFileName);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());
        final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

        assertEquals(soundFileName, alert.get("sound"));
    }

    @Test
    void setTimestamp() {
        final Long timestamp = System.currentTimeMillis();
        this.builder.setTimestamp(timestamp);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());

        assertEquals(timestamp, aps.get("timestamp"));
    }

    @ParameterizedTest
    @MethodSource("argumentsForSetEvent")
    void setEvent(LiveActivityEvent event) {
        this.builder.setEvent(event);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());

        assertEquals(event.getValue(), aps.get("event"));
    }

    @Test
    void testAddContentStateProperty() {
        final String keyForStringValue = "string";
        final String stringValue = "Hello";

        final String keyForLongValue = "integer";
        final long longValue = 12;

        final String keyForMapValue = "map";
        final Map<String, Boolean> mapValue = new HashMap<>();
        mapValue.put("boolean", true);

        this.builder.addContentStateProperty(keyForStringValue, stringValue);
        this.builder.addContentStateProperty(keyForLongValue, longValue);
        this.builder.addContentStateProperty(keyForMapValue, mapValue);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.build());
        final Map<String, Object> contentState = (Map<String, Object>) aps.get("content-state");

        assertEquals(stringValue, contentState.get(keyForStringValue));
        assertEquals(longValue, contentState.get(keyForLongValue));
        assertEquals(mapValue, contentState.get(keyForMapValue));
    }

    private static Stream<Arguments> argumentsForSetEvent() {
        return Stream.of(
                arguments(LiveActivityEvent.UPDATE),
                arguments(LiveActivityEvent.END));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractApsObjectFromPayloadString(final String payloadString) {
        final Map<String, Object> payload;

        try {
            payload = new JsonParser().parseJsonObject(payloadString);
        } catch (final ParseException e) {
            throw new IllegalArgumentException("Failed to parse JSON: " + payloadString, e);
        }

        return (Map<String, Object>) payload.get("aps");
    }
}
