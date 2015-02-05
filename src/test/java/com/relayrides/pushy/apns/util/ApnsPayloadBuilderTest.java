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

import java.nio.charset.Charset;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Before;
import org.junit.Test;

public class ApnsPayloadBuilderTest {

	private ApnsPayloadBuilder builder;
	private JSONParser parser;

	@Before
	public void setUp() {
		this.parser = new JSONParser();
		this.builder = new ApnsPayloadBuilder();
	}

	@Test
	public void testSetAlertBody() throws ParseException {
		final String alertBody = "This is a test alert message.";

		this.builder.setAlertBody(alertBody);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

			// The alert property should be a string if all we're specifying is a literal alert message
			assertEquals(alertBody, aps.get("alert"));
		}

		this.builder.setShowActionButton(false);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertEquals(alertBody, alert.get("body"));
		}
	}

	@Test
	public void testSetAlertTitle() throws ParseException {
		final String alertTitle = "This is a test alert message.";

		this.builder.setAlertTitle(alertTitle);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertEquals(alertTitle, alert.get("title"));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSetLocalizedAlertMessage() throws ParseException {
		final String alertKey = "test.alert";
		this.builder.setLocalizedAlertMessage(alertKey, null);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertEquals(alertKey, alert.get("loc-key"));
			assertNull(alert.get("loc-args"));
		}

		final String[] alertArgs = new String[] { "Moose", "helicopter" };
		this.builder.setLocalizedAlertMessage(alertKey, alertArgs);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertEquals(alertKey, alert.get("loc-key"));

			final JSONArray argsArray = (JSONArray) alert.get("loc-args");
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
	public void testSetLaunchImage() throws ParseException {
		final String launchImageFilename = "launch.png";
		this.builder.setLaunchImageFileName(launchImageFilename);

		final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
		final JSONObject alert = (JSONObject) aps.get("alert");

		assertEquals(launchImageFilename, alert.get("launch-image"));
	}

	@Test
	public void testSetShowActionButton() throws ParseException {
		this.builder.setShowActionButton(true);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

			assertNull(aps.get("alert"));
		}

		this.builder.setShowActionButton(false);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertTrue(alert.keySet().contains("action-loc-key"));
			assertNull(alert.get("action-loc-key"));
		}

		final String actionButtonKey = "action.key";
		this.builder.setLocalizedActionButtonKey(actionButtonKey);
		this.builder.setShowActionButton(true);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertEquals(actionButtonKey, alert.get("action-loc-key"));
		}

		this.builder.setShowActionButton(false);

		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			final JSONObject alert = (JSONObject) aps.get("alert");

			assertTrue(alert.keySet().contains("action-loc-key"));
			assertNull(alert.get("action-loc-key"));
		}
	}

	@Test
	public void testSetLocalizedActionButtonKey() throws ParseException {
		final String actionButtonKey = "action.key";
		this.builder.setLocalizedActionButtonKey(actionButtonKey);

		final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
		final JSONObject alert = (JSONObject) aps.get("alert");

		assertEquals(actionButtonKey, alert.get("action-loc-key"));
	}

	@Test
	public void testSetBadgeNumber() throws ParseException {
		final int badgeNumber = 4;
		this.builder.setBadgeNumber(badgeNumber);

		final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

		assertEquals(badgeNumber, ((Number) aps.get("badge")).intValue());
	}

	@Test
	public void testSetSoundFileName() throws ParseException {
		final String soundFileName = "dying-giraffe.aiff";
		this.builder.setSoundFileName(soundFileName);

		final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

		assertEquals(soundFileName, aps.get("sound"));
	}

	@Test
	public void testSetCategoryName() throws ParseException {
		final String categoryName = "INVITE";
		this.builder.setCategoryName(categoryName);

		final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());

		assertEquals(categoryName, aps.get("category"));
	}

	@Test
	public void testSetContentAvailable() throws ParseException {
		{
			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			assertNull(aps.get("content-available"));
		}

		{
			this.builder.setContentAvailable(true);

			final JSONObject aps = this.extractApsObjectFromPayloadString(this.builder.buildWithDefaultMaximumLength());
			assertEquals(1L, aps.get("content-available"));
		}
	}

	@Test
	public void testAddCustomProperty() throws ParseException {
		final String customKey = "string";
		final String customValue = "Hello";

		this.builder.addCustomProperty(customKey, customValue);

		final JSONObject payload = (JSONObject) this.parser.parse(this.builder.buildWithDefaultMaximumLength());

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

	@Test(expected = IllegalArgumentException.class)
	public void testBuildWithMaximumLengthAndUnshortenablePayload() {
		final String reallyLongAlertKey =
				"All non-glanded recruited mercenaries now engaging in training excercises are herefore and forever ordered to desist. Aforementioned activities have resulted in cost-defective damage to training areas.";

		final int maxLength = 128;

		this.builder.setLocalizedAlertMessage(reallyLongAlertKey, null);

		final String payloadString = this.builder.buildWithMaximumLength(maxLength);

		assertTrue(payloadString.getBytes(Charset.forName("UTF-8")).length <= maxLength);
	}

	private JSONObject extractApsObjectFromPayloadString(final String payloadString) throws ParseException {
		final JSONObject payload = (JSONObject) this.parser.parse(payloadString);
		return (JSONObject) payload.get("aps");
	}
}
