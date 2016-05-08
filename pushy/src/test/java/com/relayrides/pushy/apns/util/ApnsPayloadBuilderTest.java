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

import static org.junit.Assert.*;

import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ApnsPayloadBuilderTest {

    private ApnsPayloadBuilder builder;
    private Gson gson;

    private static Type MAP_OF_STRING_TO_OBJECT = new TypeToken<Map<String, Object>>(){}.getType();

    @Before
    public void setUp() {
        this.gson = new Gson();
        this.builder = new ApnsPayloadBuilder();
    }

    @Test
    public void testSetAlertBody() {
        final String alertBody = "This is a test alert message.";

        this.builder.setAlertBody(alertBody);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            // The alert property should be a string if all we're specifying is a literal alert message
            assertEquals(alertBody, aps.get("alert"));
        }

        this.builder.setShowActionButton(false);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertBody, alert.get("body"));
        }
    }

    @Test
    public void testSetAlertTitle() {
        final String alertTitle = "This is a test alert message.";

        this.builder.setAlertTitle(alertTitle);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

            @SuppressWarnings("unchecked")
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertTitle, alert.get("title"));
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

    @SuppressWarnings("unchecked")
    @Test
    public void testSetLocalizedAlertMessage() {
        final String alertKey = "test.alert";
        this.builder.setLocalizedAlertMessage(alertKey, null);

        {
            final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
            final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

            assertEquals(alertKey, alert.get("loc-key"));
            assertNull(alert.get("loc-args"));
        }

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

    @Test(expected = IllegalStateException.class)
    public void testSetAlertBodyWithExistingLocalizedAlert() {
        this.builder.setLocalizedAlertMessage("Test", null);
        this.builder.setAlertBody("Test");
    }

    @Test(expected = IllegalStateException.class)
    public void testSetLocalizedAlertWithExistingAlertBody() {
        this.builder.setAlertBody("Test");
        this.builder.setLocalizedAlertMessage("Test", null);
    }

    @Test(expected = IllegalStateException.class)
    public void testSetAlertTitleWithExistingLocalizedAlertTitle() {
        this.builder.setLocalizedAlertTitle("Test", null);
        this.builder.setAlertTitle("Test");
    }

    @Test(expected = IllegalStateException.class)
    public void testSetLocalizedAlertTitleWithExistingAlertTitle() {
        this.builder.setAlertTitle("Test");
        this.builder.setLocalizedAlertTitle("Test", null);
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
    public void testSetLocalizedActionButtonKey() {
        final String actionButtonKey = "action.key";
        this.builder.setLocalizedActionButtonKey(actionButtonKey);

        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

        @SuppressWarnings("unchecked")
        final Map<String, Object> alert = (Map<String, Object>) aps.get("alert");

        assertEquals(actionButtonKey, alert.get("action-loc-key"));
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
    public void testBuildWithMaximumLength() {
        final String reallyLongAlertMessage =
                "All non-glanded recruited mercenaries now engaging in training excercises are herefore and forever ordered to desist. Aforementioned activities have resulted in cost-defective damage to training areas.";

        final int maxLength = 128;

        this.builder.setAlertBody(reallyLongAlertMessage);

        final String payloadString = this.builder.buildWithMaximumLength(maxLength);

        assertTrue(payloadString.getBytes(Charset.forName("UTF-8")).length <= maxLength);
    }

    @Test
    public void testBuildWithMaximumLengthAndAlreadyFittingMessageBody() {
        final String shortAlertMessage = "This should just fit.";

        this.builder.setAlertBody(shortAlertMessage);
        final Map<String, Object> aps = this.extractApsObjectFromPayloadString(this.builder.buildWithMaximumLength(Integer.MAX_VALUE));

        assertEquals(shortAlertMessage, aps.get("alert"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildWithMaximumLengthAndUnshortenablePayload() {
        final String reallyLongAlertKey =
                "All non-glanded recruited mercenaries now engaging in training excercises are herefore and forever ordered to desist. Aforementioned activities have resulted in cost-defective damage to training areas.";

        final int maxLength = 128;

        this.builder.setLocalizedAlertMessage(reallyLongAlertKey, null);

        final String payloadString = this.builder.buildWithMaximumLength(maxLength);

        assertTrue(payloadString.getBytes(Charset.forName("UTF-8")).length <= maxLength);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractApsObjectFromPayloadString(final String payloadString) {
        final Map<String, Object> payload = this.gson.fromJson(payloadString, MAP_OF_STRING_TO_OBJECT);
        return (Map<String, Object>) payload.get("aps");
    }
}
