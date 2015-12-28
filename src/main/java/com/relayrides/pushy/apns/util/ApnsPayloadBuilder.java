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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * <p>A utility class for constructing JSON payloads suitable for inclusion in APNs push notifications. Payload builders
 * are reusable, but are <em>not</em> thread-safe.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @see <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ApplePushService.html#//apple_ref/doc/uid/TP40008194-CH100-SW1">
 * Local and Push Notification Programming Guide - Apple Push Notification Service - The Notification Payload</a>
 */
public class ApnsPayloadBuilder {

	private String alertBody = null;
	private String localizedAlertKey = null;
	private String[] localizedAlertArguments = null;
	private String alertTitle = null;
	private String localizedAlertTitleKey = null;
	private String[] localizedAlertTitleArguments = null;
	private String launchImageFileName = null;
	private boolean showActionButton = true;
	private String localizedActionButtonKey = null;
	private Integer badgeNumber = null;
	private String soundFileName = null;
	private String categoryName = null;
	private boolean contentAvailable = false;

	private static final String APS_KEY = "aps";
	private static final String ALERT_KEY = "alert";
	private static final String BADGE_KEY = "badge";
	private static final String SOUND_KEY = "sound";
	private static final String CATEGORY_KEY = "category";
	private static final String CONTENT_AVAILABLE_KEY = "content-available";

	private static final String ALERT_TITLE_KEY = "title";
	private static final String ALERT_BODY_KEY = "body";
	private static final String ALERT_TITLE_LOC_KEY = "title-loc-key";
	private static final String ALERT_TITLE_ARGS_KEY = "title-loc-args";
	private static final String ACTION_LOC_KEY = "action-loc-key";
	private static final String ALERT_LOC_KEY = "loc-key";
	private static final String ALERT_ARGS_KEY = "loc-args";
	private static final String LAUNCH_IMAGE_KEY = "launch-image";

	private final HashMap<String, Object> customProperties = new HashMap<String, Object>();

	private static final Charset UTF8 = Charset.forName("UTF-8");

	/**
	 * The name of the iOS default push notification sound ({@value DEFAULT_SOUND_FILENAME}).
	 *
	 * @see com.relayrides.pushy.apns.util.ApnsPayloadBuilder#setSoundFileName(String)
	 */
	public static final String DEFAULT_SOUND_FILENAME = "default";

	/**
	 * Constructs a new payload builder.
	 */
	public ApnsPayloadBuilder() {
	}

	/**
	 * <p>Sets the literal text of the alert message to be shown for the push notification. A literal alert message may
	 * not be set if a localized alert message key is already specified.</p>
	 *
	 * <p>By default, no message is shown.</p>
	 *
	 * @param alertBody the literal message to be shown for this push notification
	 *
	 * @see ApnsPayloadBuilder#setLocalizedAlertMessage(String, String[])
	 */
	public ApnsPayloadBuilder setAlertBody(final String alertBody) {
		if (alertBody != null && this.localizedAlertKey != null) {
			throw new IllegalStateException("Cannot set a literal alert body when a localized alert key has already been set.");
		}

		this.alertBody = alertBody;

		return this;
	}

	/**
	 * <p>Sets the key of a message in the receiving app's localized string list to be shown for the push notification.
	 * The message in the app's string list may optionally have placeholders, which will be populated by values from the
	 * given {@code alertArguments}.</p>
	 *
	 * <p>By default, no message is shown.</p>
	 *
	 * @param localizedAlertKey a key to a string in the receiving app's localized string list
	 * @param alertArguments arguments to populate placeholders in the localized alert string; may be {@code null}
	 */
	public ApnsPayloadBuilder setLocalizedAlertMessage(final String localizedAlertKey, final String[] alertArguments) {
		if (localizedAlertKey != null && this.alertBody != null) {
			throw new IllegalStateException("Cannot set a localized alert key when a literal alert body has already been set.");
		}

		if (localizedAlertKey == null && alertArguments != null) {
			throw new IllegalArgumentException("Cannot set localized alert arguments without a localized alert message key.");
		}

		this.localizedAlertKey = localizedAlertKey;
		this.localizedAlertArguments = alertArguments;

		return this;
	}

	/**
	 * <p>Sets a short description of the notification purpose. The Apple Watch will display this as part of the notification.
	 * According to Apple's documentation, this should be:</p>
	 *
	 *  <blockquote>A short string describing the purpose of the notification. Apple Watch displays this string as part of the notification interface.
	 *  This string is displayed only briefly and should be crafted so that it can be understood quickly.</blockquote>
	 *
	 * @param alertTitle the description to be shown for this push notification
	 *
	 * @see ApnsPayloadBuilder#setLocalizedAlertTitle(String, String[])
	 */
	public ApnsPayloadBuilder setAlertTitle(final String alertTitle) {
		if (alertTitle != null && this.localizedAlertTitleKey != null) {
			throw new IllegalStateException("Cannot set a literal alert title when a localized alert title key has already been set.");
		}

		this.alertTitle = alertTitle;

		return this;
	}

	/**
	 * <p>Sets the key of the title string in the receiving app's localized string list to be shown for the push notification.
	 * The message in the app's string list may optionally have placeholders, which will be populated by values from the
	 * given {@code alertArguments}.</p>
	 *
	 * @param localizedAlertTitleKey a key to a string in the receiving app's localized string list
	 * @param alertTitleArguments arguments to populate placeholders in the localized alert string; may be {@code null}
	 */
	public ApnsPayloadBuilder setLocalizedAlertTitle(final String localizedAlertTitleKey, final String[] alertTitleArguments) {
		if (localizedAlertTitleKey != null && this.alertTitle != null) {
			throw new IllegalStateException("Cannot set a localized alert key when a literal alert body has already been set.");
		}

		if (localizedAlertTitleKey == null && alertTitleArguments != null) {
			throw new IllegalArgumentException("Cannot set localized alert arguments without a localized alert message key.");
		}

		this.localizedAlertTitleKey = localizedAlertTitleKey;
		this.localizedAlertTitleArguments = alertTitleArguments;

		return this;
	}

	/**
	 * <p>Sets the image to be shown when the receiving app launches in response to this push notification. According
	 * to Apple's documentation, this should be:</p>
	 *
	 * <blockquote>The filename of an image file in the application bundle; it may include the extension or omit it.
	 * The image is used as the launch image when users tap the action button or move the action slider. If this
	 * property is not specified, the system either uses the previous snapshot, uses the image identified by the
	 * {@code UILaunchImageFile} key in the application’s {@code Info.plist} file, or falls back to
	 * {@code Default.png}.</blockquote>
	 *
	 * @param launchImageFilename the filename of an image file in the receiving app's bundle to be shown when launching
	 * the app from the push notification
	 */
	public ApnsPayloadBuilder setLaunchImageFileName(final String launchImageFilename) {
		this.launchImageFileName = launchImageFilename;
		return this;
	}

	/**
	 * <p>Sets whether an &quot;action&quot; button should be shown if the push notification is displayed as an alert.
	 * If {@code true} and no localized action button key is set, the default label (defined by the receiving operating
	 * system) is used. If @{code true} and a localized action button key is set, the string for that key is used as
	 * the label of the action button. If {@code false}, no action button is shown under any circumstances</p>
	 *
	 * <p>By default, an action button will be shown.</p>
	 *
	 * @param showActionButton {@code true} to show an action button when the push notification is presented as an
	 * alert or {@code false} to show an alert with no action button
	 */
	public ApnsPayloadBuilder setShowActionButton(final boolean showActionButton) {
		this.showActionButton = showActionButton;
		return this;
	}

	/**
	 * <p>Sets the key of a string in the receiving app's localized string list to be used as the label of the
	 * &quot;action&quot; button if the push notification is displayed as an alert. By default, the OS-default label
	 * will be used for the action button.</p>
	 *
	 * @param localizedActionButtonKey a key to a string in the receiving app's localized string list
	 */
	public ApnsPayloadBuilder setLocalizedActionButtonKey(final String localizedActionButtonKey) {
		this.localizedActionButtonKey = localizedActionButtonKey;
		return this;
	}

	/**
	 * <p>Sets the number to display as the badge of the icon of the application that receives the push notification.
	 * If the badge number is 0, the badge is removed from the application icon. If {@code null}, the badge is left in
	 * its current state. By default, no change is made to the badge.</p>
	 *
	 * @param badgeNumber the number to display as the badge of application or {@code null} to leave the badge unchanged
	 */
	public ApnsPayloadBuilder setBadgeNumber(final Integer badgeNumber) {
		this.badgeNumber = badgeNumber;
		return this;
	}

	/**
	 * <p>Sets the name of the action category name for interactive remote notifications.</p>
	 *
	 * @param categoryName the action category name
	 */
	public ApnsPayloadBuilder setCategoryName(final String categoryName) {
		this.categoryName = categoryName;
		return this;
	}

	/**
	 * <p>Sets the name of the sound file to play when the push notification is received. According to Apple's
	 * documentation, the value here should be:</p>
	 *
	 * <blockquote>...the name of a sound file in the application bundle. The sound in this file is played as an alert.
	 * If the sound file doesn't exist or {@code default} is specified as the value, the default alert sound is
	 * played.</blockquote>
	 *
	 * <p>By default, no sound is included in the push notification.</p>
	 *
	 * @param soundFileName the name of the sound file to play, or {@code null} to send no sound
	 *
	 * @see com.relayrides.pushy.apns.util.ApnsPayloadBuilder#DEFAULT_SOUND_FILENAME
	 */
	public ApnsPayloadBuilder setSoundFileName(final String soundFileName) {
		this.soundFileName = soundFileName;
		return this;
	}

	/**
	 * <p>Sets whether the payload under construction should contain a flag that indicates that new content is available
	 * to be downloaded in the background by the receiving app. By default, no content availability flag is included
	 * in the payload.</p>
	 *
	 * @param contentAvailable {@code true} to include a flag that indicates that new content is available to be
	 * downloaded in the background or {@code false} otherwise
	 *
	 * @see <a href="https://developer.apple.com/library/ios/documentation/iPhone/Conceptual/iPhoneOSProgrammingGuide/ManagingYourApplicationsFlow/ManagingYourApplicationsFlow.html#//apple_ref/doc/uid/TP40007072-CH4-SW24">
	 * iOS App Programming Guide - App States and Multitasking - Background Execution and Multitasking - Implementing
	 * Long-Running Background Tasks</a>
	 */
	public ApnsPayloadBuilder setContentAvailable(final boolean contentAvailable) {
		this.contentAvailable = contentAvailable;
		return this;
	}

	/**
	 * <p>Adds a custom property to the payload. Values are mapped to JSON types according to the mapping table at
	 * <a href="https://code.google.com/p/json-simple/">https://code.google.com/p/json-simple/</a>.</p>
	 *
	 * <p>According to Apple's documentation:</p>
	 *
	 * <blockquote>Providers can specify custom payload values outside the Apple-reserved {@code aps} namespace. Custom
	 * values must use the JSON structured and primitive types: dictionary (object), array, string, number, and Boolean.
	 * You should not include customer information (or any sensitive data) as custom payload data. Instead, use it for
	 * such purposes as setting context (for the user interface) or internal metrics. For example, a custom payload
	 * value might be a conversation identifier for use by an instant-message client application or a timestamp
	 * identifying when the provider sent the notification. Any action associated with an alert message should not be
	 * destructive—for example, it should not delete data on the device.</blockquote>
	 *
	 * @param key the key of the custom property in the payload object
	 * @param value the value of the custom property
	 */
	public ApnsPayloadBuilder addCustomProperty(final String key, final Object value) {
		this.customProperties.put(key, value);
		return this;
	}

	/**
	 * <p>Returns a JSON representation of the push notification payload under construction. If the payload length is
	 * longer than the maximum allowed for the given iOS version, the literal alert body will be shortened if possible.
	 * If the alert body cannot be shortened or is not present, an {@code IllegalArgumentException} is thrown.</p>
	 *
	 * @return a JSON representation of the payload under construction (possibly with an abbreviated alert body)
	 */
	public String buildWithMaximumLengthForIOSVersion(final IOSVersion version) {
		final int maximumPayloadLength;

		switch (version) {
			case IOS_7_AND_OLDER: {
				maximumPayloadLength = 256;
				break;
			}

			case IOS_8_AND_NEWER: {
				maximumPayloadLength = 2048;
				break;
			}

			default: {
				throw new IllegalArgumentException(String.format("Unexpected iOS version."));
			}
		}

		return this.buildWithMaximumLength(maximumPayloadLength);
	}

	/**
	 * <p>Returns a JSON representation of the push notification payload under construction. If the payload length is
	 * longer than the given maximum, the literal alert body will be shortened if possible. If the alert body cannot be
	 * shortened or is not present, an {@code IllegalArgumentException} is thrown.</p>
	 *
	 * @param maximumPayloadLength the maximum length of the payload in bytes
	 *
	 * @return a JSON representation of the payload under construction (possibly with an abbreviated alert body)
	 */
	@SuppressWarnings("unchecked")
	public String buildWithMaximumLength(final int maximumPayloadLength) {
		final JSONObject payload = new JSONObject();

		{
			final JSONObject aps = new JSONObject();

			if (this.badgeNumber != null) {
				aps.put(BADGE_KEY, this.badgeNumber);
			}

			if (this.soundFileName != null) {
				aps.put(SOUND_KEY, this.soundFileName);
			}

			if (this.categoryName != null) {
				aps.put(CATEGORY_KEY, this.categoryName);
			}

			if (this.contentAvailable) {
				aps.put(CONTENT_AVAILABLE_KEY, 1);
			}

			final Object alertObject = this.createAlertObject();

			if (alertObject != null) {
				aps.put(ALERT_KEY, alertObject);
			}

			payload.put(APS_KEY, aps);
		}

		for (final Map.Entry<String, Object> entry : this.customProperties.entrySet()) {
			payload.put(entry.getKey(), entry.getValue());
		}

		final String payloadString = payload.toJSONString();
		final int initialPayloadLength = payloadString.getBytes(UTF8).length;

		if (initialPayloadLength <= maximumPayloadLength) {
			return payloadString;
		} else {
			// TODO This could probably be more efficient
			if (this.alertBody != null) {
				this.replaceMessageBody(payload, "");
				final int payloadLengthWithEmptyMessage = payload.toJSONString().getBytes(UTF8).length;

				if (payloadLengthWithEmptyMessage > maximumPayloadLength) {
					throw new IllegalArgumentException("Payload exceeds maximum length even with an empty message body.");
				}

				int maximumMessageBodyLength = maximumPayloadLength - payloadLengthWithEmptyMessage;

				this.replaceMessageBody(payload, this.abbreviateString(this.alertBody, maximumMessageBodyLength--));

				while (payload.toJSONString().getBytes(UTF8).length > maximumPayloadLength) {
					this.replaceMessageBody(payload, this.abbreviateString(this.alertBody, maximumMessageBodyLength--));
				}

				return payload.toJSONString();

			} else {
				throw new IllegalArgumentException(String.format(
						"Payload length is %d bytes (with a maximum of %d bytes) and cannot be shortened.",
						initialPayloadLength, maximumPayloadLength));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void replaceMessageBody(final JSONObject payload, final String messageBody) {
		final JSONObject aps = (JSONObject) payload.get(APS_KEY);
		final Object alert = aps.get(ALERT_KEY);

		if (alert != null) {
			if (alert instanceof String) {
				aps.put(ALERT_KEY, messageBody);
			} else {
				final JSONObject alertObject = (JSONObject) alert;

				if (alertObject.get(ALERT_BODY_KEY) != null) {
					alertObject.put(ALERT_BODY_KEY, messageBody);
				} else {
					throw new IllegalArgumentException("Payload has no message body.");
				}
			}
		} else {
			throw new IllegalArgumentException("Payload has no message body.");
		}
	}

	private final String abbreviateString(final String string, final int maximumLength) {
		if (string.length() <= maximumLength) {
			return string;
		} else {
			if (maximumLength <= 3) {
				throw new IllegalArgumentException("Cannot abbreviate string to fewer than three characters.");
			}

			// TODO Actually use a real ellipses character when https://code.google.com/p/json-simple/issues/detail?id=25 gets resolved
			// TODO Make this locale-sensitive
			return string.substring(0, maximumLength - 3) + "...";
		}
	}

	@SuppressWarnings("unchecked")
	private Object createAlertObject() {
		if (this.hasAlertContent()) {
			if (this.shouldRepresentAlertAsString()) {
				return this.alertBody;
			} else {
				final JSONObject alert = new JSONObject();

				if (this.alertBody != null) {
					alert.put(ALERT_BODY_KEY, this.alertBody);
				}

				if (this.alertTitle != null) {
					alert.put(ALERT_TITLE_KEY, this.alertTitle);
				}

				if (this.showActionButton) {
					if (this.localizedActionButtonKey != null) {
						alert.put(ACTION_LOC_KEY, this.localizedActionButtonKey);
					}
				} else {
					// To hide the action button, the key needs to be present, but the value needs to be null
					alert.put(ACTION_LOC_KEY, null);
				}

				if (this.localizedAlertKey != null) {
					alert.put(ALERT_LOC_KEY, this.localizedAlertKey);

					if (this.localizedAlertArguments != null) {
						final JSONArray alertArgs = new JSONArray();

						for (final String arg : this.localizedAlertArguments) {
							alertArgs.add(arg);
						}

						alert.put(ALERT_ARGS_KEY, alertArgs);
					}
				}

				if (this.localizedAlertTitleKey != null) {
					alert.put(ALERT_TITLE_LOC_KEY, this.localizedAlertTitleKey);

					if (this.localizedAlertTitleArguments != null) {
						final JSONArray alertTitleArgs = new JSONArray();

						for (final String arg : this.localizedAlertTitleArguments) {
							alertTitleArgs.add(arg);
						}

						alert.put(ALERT_TITLE_ARGS_KEY, alertTitleArgs);
					}
				}

				if (this.launchImageFileName != null) {
					alert.put(LAUNCH_IMAGE_KEY, this.launchImageFileName);
				}

				return alert;
			}
		} else {
			return null;
		}
	}

	/**
	 * Checks whether the notification under construction has content that warrants an {@code alert} section.
	 *
	 * @return {@code true} if this notification should have an {@code alert} section or {@code false} otherwise
	 */
	private boolean hasAlertContent() {
		return	this.alertBody != null ||
				this.alertTitle != null ||
				this.localizedAlertTitleKey != null ||
				this.localizedAlertKey != null ||
				this.localizedActionButtonKey != null ||
				this.launchImageFileName != null ||
				this.showActionButton == false;
	}

	/**
	 * <p>Checks whether the alert message for the push notification should be represented as a string or a
	 * dictionary. According to Apple's documentation:</p>
	 *
	 * <blockquote>If you want the device to display the message text as-is in an alert that has both the Close and
	 * View buttons, then specify a string as the direct value of {@code alert}. Don't specify a dictionary as the
	 * value of {@code alert} if the dictionary only has the {@code body} property.</blockquote>
	 *
	 * @return {@code true} if the alert message (if present) should be represented as a string or {@code false} if it
	 * should be represented as a dictionary
	 */
	private boolean shouldRepresentAlertAsString() {
		return this.alertBody != null
				&& this.launchImageFileName == null
				&& this.showActionButton
				&& this.localizedActionButtonKey == null
				&& this.alertTitle == null
				&& this.localizedAlertTitleKey == null
				&& this.localizedAlertKey == null
				&& this.localizedAlertArguments == null
				&& this.localizedAlertTitleArguments == null;
	}
}
