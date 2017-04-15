/* Copyright (c) 2013 RelayRides
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

package com.relayrides.pushy.apns.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Type;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class ApnsPayloadBuilderTest {

    private ApnsPayloadBuilder builder;
    private Gson gson;

    private static Type MAP_OF_STRING_TO_OBJECT = new TypeToken<Map<String, Object>>(){}.getType();

    @Before
    public void setUp() {
        this.gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();
        this.builder = new ApnsPayloadBuilder();
    }

    @Test
    public void testSetAlertBody() {
        final String alertBody = "This is a test alert message.";

        this.builder.setLocalizedAlertMessage("test.alert");
        this.builder.setAlertBody(alertBody);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertBody, alert.get("body"));
        }

        this.builder.setPreferStringRepresentationForAlerts(true);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            assertEquals(alertBody, aps.get("alert"));
        }

        this.builder.setLocalizedAlertMessage("test.alert");
        this.builder.setAlertBody(alertBody);
        this.builder.setShowActionButton(false);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertBody, alert.get("body"));
            assertNull(alert.get("loc-key"));
            assertNull(alert.get("loc-args"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSetLocalizedAlertBody() {
        final String alertKey = "test.alert";

        this.builder.setAlertBody("Alert body!");
        this.builder.setLocalizedAlertMessage(alertKey);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertKey, alert.get("loc-key"));
            assertNull(alert.get("loc-args"));
            assertNull(alert.get("body"));
        }

        // We're happy here as long as nothing explodes
        this.builder.setLocalizedAlertMessage(alertKey, null);

        final String[] alertArgs = new String[] { "Moose", "helicopter" };
        this.builder.setLocalizedAlertMessage(alertKey, alertArgs);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertKey, alert.get("loc-key"));

            final List<Object> argsArray = (List<Object>) alert.get("loc-args");
            assertEquals(alertArgs.length, argsArray.size());
            assertTrue(argsArray.containsAll(java.util.Arrays.asList(alertArgs)));
        }
    }

    @Test
    public void testSetAlertTitle() {
        final String alertTitle = "This is a test alert message.";

        this.builder.setLocalizedAlertTitle("alert.title");
        this.builder.setAlertTitle(alertTitle);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertTitle, alert.get("title"));
            assertNull(alert.get("title-loc-key"));
            assertNull(alert.get("title-loc-args"));
        }
    }

    @Test
    public void testSetLocalizedAlertTitle() {
        final String localizedAlertTitleKey = "alert.title";

        this.builder.setAlertTitle("Alert title!");
        this.builder.setLocalizedAlertTitle(localizedAlertTitleKey);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(localizedAlertTitleKey, alert.get("title-loc-key"));
            assertNull(alert.get("title-loc-args"));
            assertNull(alert.get("title"));
        }

        // We're happy here as long as nothing explodes
        this.builder.setLocalizedAlertTitle(localizedAlertTitleKey, null);

        final String[] alertArgs = new String[] { "Moose", "helicopter" };
        this.builder.setLocalizedAlertTitle(localizedAlertTitleKey, alertArgs);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(localizedAlertTitleKey, alert.get("title-loc-key"));

            @SuppressWarnings("unchecked")
            final List<Object> argsArray = (List<Object>) alert.get("title-loc-args");
            assertEquals(alertArgs.length, argsArray.size());
            assertTrue(argsArray.containsAll(java.util.Arrays.asList(alertArgs)));
        }
    }

    @Test
    public void testSetAlertSubtitle() {
        final String alertSubtitle = "This is a test alert message.";

        this.builder.setLocalizedAlertSubtitle("alert.subtitle");
        this.builder.setAlertSubtitle(alertSubtitle);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertSubtitle, alert.get("subtitle"));
            assertNull(alert.get("subtitle-loc-key"));
            assertNull(alert.get("subtitle-loc-args"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSetLocalizedAlertSubitle() {
        final String subtitleKey = "test.subtitle";

        this.builder.setAlertSubtitle("Subtitle!");
        this.builder.setLocalizedAlertSubtitle(subtitleKey);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(subtitleKey, alert.get("subtitle-loc-key"));
            assertNull(alert.get("subtitle-loc-args"));
            assertNull(alert.get("subtitle"));
        }

        // We're happy here as long as nothing explodes
        this.builder.setLocalizedAlertSubtitle(subtitleKey, null);

        final String[] subtitleArgs = new String[] { "Moose", "helicopter" };
        this.builder.setLocalizedAlertSubtitle(subtitleKey, subtitleArgs);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(subtitleKey, alert.get("subtitle-loc-key"));

            final List<Object> argsArray = (List<Object>) alert.get("subtitle-loc-args");
            assertEquals(subtitleArgs.length, argsArray.size());
            assertTrue(argsArray.containsAll(java.util.Arrays.asList(subtitleArgs)));
        }
    }

    @Test
    public void testSetAlertTitleAndBody() {
        final String alertTitle = "This is a short alert title";
        final String alertBody = "This is a longer alert body";

        this.builder.setAlertBody(alertBody);
        this.builder.setAlertTitle(alertTitle);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertTitle, alert.get("title"));
            assertEquals(alertBody, alert.get("body"));
        }
    }

    @Test
    public void testSetLaunchImage() {
        final String launchImageFilename = "launch.png";
        this.builder.setLaunchImageFileName(launchImageFilename);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        @SuppressWarnings("unchecked")
        final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

        assertEquals(launchImageFilename, alert.get("launch-image"));
    }

    @Test
    public void testSetShowActionButton() {
        this.builder.setShowActionButton(true);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            assertNull(aps.get("alert"));
        }

        this.builder.setShowActionButton(false);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertTrue(alert.keySet().contains("action-loc-key"));
            assertNull(alert.get("action-loc-key"));
        }

        final String actionButtonKey = "action.key";
        this.builder.setLocalizedActionButtonKey(actionButtonKey);
        this.builder.setShowActionButton(true);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(actionButtonKey, alert.get("action-loc-key"));
        }

        this.builder.setShowActionButton(false);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertTrue(alert.keySet().contains("action-loc-key"));
            assertNull(alert.get("action-loc-key"));
        }
    }

    @Test
    public void testSetActionButtonLabel() {
        final String actionButtonLabel = "Bam! Pow!";

        this.builder.setLocalizedActionButtonKey("action.key");
        this.builder.setActionButtonLabel(actionButtonLabel);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        @SuppressWarnings("unchecked")
        final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

        assertEquals(actionButtonLabel, alert.get("action"));
        assertNull(alert.get("action-loc-key"));
    }

    @Test
    public void testSetLocalizedActionButtonKey() {
        final String actionButtonKey = "action.key";

        this.builder.setActionButtonLabel("Literal action button label");
        this.builder.setLocalizedActionButtonKey(actionButtonKey);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        @SuppressWarnings("unchecked")
        final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

        assertEquals(actionButtonKey, alert.get("action-loc-key"));
        assertNull(alert.get("action"));
    }

    @Test
    public void testSetBadgeNumber() {
        final int badgeNumber = 4;
        this.builder.setBadgeNumber(badgeNumber);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        assertEquals(badgeNumber, ((Number) aps.get("badge")).intValue());
    }

    @Test
    public void testSetSoundFileName() {
        final String soundFileName = "dying-giraffe.aiff";
        this.builder.setSoundFileName(soundFileName);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        assertEquals(soundFileName, aps.get("sound"));
    }

    @Test
    public void testSetCategoryName() {
        final String categoryName = "INVITE";
        this.builder.setCategoryName(categoryName);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        assertEquals(categoryName, aps.get("category"));
    }

    @Test
    public void testSetContentAvailable() {
        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            assertNull(aps.get("content-available"));
        }

        {
            this.builder.setContentAvailable(true);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            assertEquals(1, ((Number) aps.get("content-available")).intValue());
        }
    }

    @Test
    public void testSetMutableContent() {
        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            assertNull(aps.get("mutable-content"));
        }

        {
            this.builder.setMutableContent(true);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            assertEquals(1, ((Number) aps.get("mutable-content")).intValue());
        }
    }

    @Test
    public void testSetThreadId() {
        final String threadId = "example.thread.identifier";

        this.builder.setThreadId(threadId);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
        assertEquals(threadId, aps.get("thread-id"));
    }

    @Test
    public void testAddCustomProperty() {
        final String customKey = "string";
        final String customValue = "Hello";

        this.builder.addCustomProperty(customKey, customValue);

        @SuppressWarnings("unchecked")
        final Map<String, Object> payload = (Map<String, Object>) this.gson.fromJson(
                this.builder.buildWithDefaultMaximumLength(), MAP_OF_STRING_TO_OBJECT);

        assertEquals(customValue, payload.get(customKey));
    }

    @Test
    public void testSetUrlArgumentsList() {
        {
            final List<String> arguments = new ArrayList<>();
            arguments.add("first");
            arguments.add("second");
            arguments.add("third");

            this.builder.setUrlArguments(arguments);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            assertEquals(arguments, aps.get("url-args"));
        }

        {
            final List<String> arguments = new ArrayList<>();
            this.builder.setUrlArguments(arguments);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            assertEquals(arguments, aps.get("url-args"));
        }

        {
            this.builder.setUrlArguments((List<String>) null);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            assertNull(aps.get("url-args"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSetUrlArgumentsArray() {
        {
            this.builder.setUrlArguments("first", "second", "third");

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            final List<String> argumentsFromPayload = (List<String>) aps.get("url-args");

            assertEquals(3, argumentsFromPayload.size());
            assertEquals("first", argumentsFromPayload.get(0));
            assertEquals("second", argumentsFromPayload.get(1));
            assertEquals("third", argumentsFromPayload.get(2));
        }

        {
            final String[] arguments = new String[0];
            this.builder.setUrlArguments(arguments);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            assertTrue(((List<String>) aps.get("url-args")).isEmpty());
        }

        {
            this.builder.setUrlArguments((String[]) null);

            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            assertNull(aps.get("url-args"));
        }
    }

    @Test
    public void testBuildWithMaximumLength() {
        final String reallyLongAlertMessage = "All non-glanded recruited mercenaries now engaging in training " +
                "excercises are herefore and forever ordered to desist. Aforementioned activities have resulted in " +
                "cost-defective damage to training areas.";

        final int maxLength = 128;

        this.builder.setAlertBody(reallyLongAlertMessage);

        final String payloadString = this.builder.buildWithMaximumLength(maxLength);

        assertTrue(reallyLongAlertMessage.getBytes(StandardCharsets.UTF_8).length > maxLength);
        assertTrue(payloadString.getBytes(StandardCharsets.UTF_8).length == maxLength);
    }

    @Test
    public void testBuildWithMaximumLengthAndAlreadyFittingMessageBody() {
        final String shortAlertMessage = "This should just fit.";

        this.builder.setAlertBody(shortAlertMessage);
        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithMaximumLength(Integer.MAX_VALUE));

        @SuppressWarnings("unchecked")
        final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

        assertEquals(shortAlertMessage, alert.get("body"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithMaximumLengthAndUnshortenablePayload() {
        final String reallyLongAlertKey =
                "All non-glanded recruited mercenaries now engaging in training excercises are herefore and forever ordered to desist. Aforementioned activities have resulted in cost-defective damage to training areas.";

        final int maxLength = 128;

        this.builder.setLocalizedAlertMessage(reallyLongAlertKey);

        final String payloadString = this.builder.buildWithMaximumLength(maxLength);

        assertTrue(payloadString.getBytes(StandardCharsets.UTF_8).length <= maxLength);
    }

    @Test
    public void testGetSizeOfJsonEscapedUtf8Character() {
        final CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();

        for (int codePoint = Character.MIN_CODE_POINT; codePoint < Character.MAX_CODE_POINT; codePoint++) {
            if (encoder.canEncode((char) codePoint)) {
                // We subtract 2 here for the quotes that will appear on either end of a JSON string
                assertEquals("Escaped/encoded lengths should match for code point " + codePoint,
                        this.gson.toJson(String.valueOf((char) codePoint)).getBytes(StandardCharsets.UTF_8).length - 2,
                        ApnsPayloadBuilder.getSizeOfJsonEscapedUtf8Character((char) codePoint));
            }
        }
    }

    @Test
    public void testGetLengthOfJsonEscapedUtf8StringFittingSize() {
        {
            final String stringThatFits = "Test!";

            assertEquals(stringThatFits.length(),
                    ApnsPayloadBuilder.getLengthOfJsonEscapedUtf8StringFittingSize(stringThatFits, Integer.MAX_VALUE));
        }

        assertEquals(4, ApnsPayloadBuilder.getLengthOfJsonEscapedUtf8StringFittingSize("This string is too long.", 4));
        assertEquals(2, ApnsPayloadBuilder.getLengthOfJsonEscapedUtf8StringFittingSize("\n\tThis string has escaped characters.", 4));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractApsObjectFromPayloadString(final String payloadString) {
        final Map<String, Object> payload = this.gson.fromJson(payloadString, MAP_OF_STRING_TO_OBJECT);
        return (Map<String, Object>) payload.get("aps");
    }
}
