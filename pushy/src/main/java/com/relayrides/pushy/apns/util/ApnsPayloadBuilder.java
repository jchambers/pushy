/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns.util;

import java.io.CharArrayWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * <p>A utility class for constructing JSON payloads suitable for inclusion in APNs push notifications. Payload builders
 * are reusable, but are <em>not</em> thread-safe.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href=
 *      "https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/PayloadKeyReference.html">
 *      Local and Push Notification Programming Guide - Apple Push Notification Service - Payload Key Reference</a>
 */
public class ApnsPayloadBuilder {

    private String alertBody = null;
    private String localizedAlertKey = null;
    private String[] localizedAlertArguments = null;
    private String alertTitle = null;
    private String localizedAlertTitleKey = null;
    private String[] localizedAlertTitleArguments = null;
    private String alertSubtitle = null;
    private String localizedAlertSubtitleKey = null;
    private String[] localizedAlertSubtitleArguments = null;
    private String launchImageFileName = null;
    private boolean showActionButton = true;
    private String actionButtonLabel = null;
    private String localizedActionButtonKey = null;
    private Integer badgeNumber = null;
    private String soundFileName = null;
    private String categoryName = null;
    private boolean contentAvailable = false;
    private boolean mutableContent = false;
    private String threadId = null;
    private String[] urlArguments = null;

    private boolean preferStringRepresentationForAlerts = false;

    private final CharArrayWriter buffer = new CharArrayWriter(DEFAULT_MAXIMUM_PAYLOAD_SIZE / 4);

    private static final String APS_KEY = "aps";
    private static final String ALERT_KEY = "alert";
    private static final String BADGE_KEY = "badge";
    private static final String SOUND_KEY = "sound";
    private static final String CATEGORY_KEY = "category";
    private static final String CONTENT_AVAILABLE_KEY = "content-available";
    private static final String MUTABLE_CONTENT_KEY = "mutable-content";
    private static final String THREAD_ID_KEY = "thread-id";
    private static final String URL_ARGS_KEY = "url-args";

    private static final String ALERT_TITLE_KEY = "title";
    private static final String ALERT_TITLE_LOC_KEY = "title-loc-key";
    private static final String ALERT_TITLE_ARGS_KEY = "title-loc-args";
    private static final String ALERT_SUBTITLE_KEY = "subtitle";
    private static final String ALERT_SUBTITLE_LOC_KEY = "subtitle-loc-key";
    private static final String ALERT_SUBTITLE_ARGS_KEY = "subtitle-loc-args";
    private static final String ALERT_BODY_KEY = "body";
    private static final String ALERT_LOC_KEY = "loc-key";
    private static final String ALERT_ARGS_KEY = "loc-args";
    private static final String ACTION_KEY = "action";
    private static final String ACTION_LOC_KEY = "action-loc-key";
    private static final String LAUNCH_IMAGE_KEY = "launch-image";

    private final HashMap<String, Object> customProperties = new HashMap<>();

    private static final String ABBREVIATION_SUBSTRING = "…";

    private static final Gson gson = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    /**
     * The name of the iOS default push notification sound
     * ({@value com.relayrides.pushy.apns.util.ApnsPayloadBuilder#DEFAULT_SOUND_FILENAME}).
     *
     * @see com.relayrides.pushy.apns.util.ApnsPayloadBuilder#setSoundFileName(String)
     */
    public static final String DEFAULT_SOUND_FILENAME = "default";

    /**
     * The default maximum size, in bytes, for a push notification payload.
     *
     * @see com.relayrides.pushy.apns.util.ApnsPayloadBuilder#buildWithDefaultMaximumLength()
     */
    public static final int DEFAULT_MAXIMUM_PAYLOAD_SIZE = 4096;

    /**
     * Sets whether this payload builder will attempt to represent alerts as strings when possible. Older versions of
     * the APNs specification recommended representing alerts as strings when only a literal alert body was provided,
     * but recent versions recommend representing alerts as dictionaries regardless. This method is provided primarily
     * for backward-compatibility. By default, payload builders will always represent alerts as dictionaries.
     *
     * @param preferStringRepresentationForAlerts if {@code true}, then this payload builder will represent alerts as
     * strings when possible; otherwise, alerts will always be represented as dictionaries
     *
     * @return a reference to this payload builder
     *
     * @since 0.8.2
     */
    public ApnsPayloadBuilder setPreferStringRepresentationForAlerts(final boolean preferStringRepresentationForAlerts) {
        this.preferStringRepresentationForAlerts = preferStringRepresentationForAlerts;
        return this;
    }

    /**
     * <p>Sets the literal text of the alert message to be shown for the push notification. Clears any previously-set
     * localized alert message key and arguments.</p>
     *
     * <p>By default, no message is shown.</p>
     *
     * @param alertBody the literal message to be shown for this push notification
     *
     * @return a reference to this payload builder
     *
     * @see ApnsPayloadBuilder#setLocalizedAlertMessage(String, String...)
     */
    public ApnsPayloadBuilder setAlertBody(final String alertBody) {
        this.alertBody = alertBody;

        this.localizedAlertKey = null;
        this.localizedAlertArguments = null;

        return this;
    }

    /**
     * <p>Sets the key of a message in the receiving app's localized string list to be shown for the push notification.
     * The message in the app's string list may optionally have placeholders, which will be populated by values from the
     * given {@code alertArguments}. Clears any previously-set literal alert body.</p>
     *
     * <p>By default, no message is shown.</p>
     *
     * @param localizedAlertKey a key to a string in the receiving app's localized string list
     * @param alertArguments arguments to populate placeholders in the localized alert string; may be {@code null}
     *
     * @return a reference to this payload builder
     *
     * @see ApnsPayloadBuilder#setAlertBody(String)
     */
    public ApnsPayloadBuilder setLocalizedAlertMessage(final String localizedAlertKey, final String... alertArguments) {
        this.localizedAlertKey = localizedAlertKey;
        this.localizedAlertArguments = (alertArguments != null && alertArguments.length > 0) ? alertArguments : null;

        this.alertBody = null;

        return this;
    }

    /**
     * <p>Sets a short description of the notification purpose. Clears any previously-set localized title key and
     * arguments. The Apple Watch will display the title as part of the notification. According to Apple's
     * documentation, this should be:</p>
     *
     * <blockquote>A short string describing the purpose of the notification. Apple Watch displays this string as part
     * of the notification interface. This string is displayed only briefly and should be crafted so that it can be
     * understood quickly.</blockquote>
     *
     * <p>By default, no title is included.</p>
     *
     * @param alertTitle the description to be shown for this push notification
     *
     * @return a reference to this payload builder
     *
     * @see ApnsPayloadBuilder#setLocalizedAlertTitle(String, String...)
     */
    public ApnsPayloadBuilder setAlertTitle(final String alertTitle) {
        this.alertTitle = alertTitle;

        this.localizedAlertTitleKey = null;
        this.localizedAlertTitleArguments = null;

        return this;
    }

    /**
     * <p>Sets the key of the title string in the receiving app's localized string list to be shown for the push
     * notification. Clears any previously-set literal alert title. The message in the app's string list may optionally
     * have placeholders, which will be populated by values from the given {@code alertArguments}.</p>
     *
     * @param localizedAlertTitleKey a key to a string in the receiving app's localized string list
     * @param alertTitleArguments arguments to populate placeholders in the localized alert string; may be {@code null}
     *
     * @return a reference to this payload builder
     */
    public ApnsPayloadBuilder setLocalizedAlertTitle(final String localizedAlertTitleKey, final String... alertTitleArguments) {
        this.localizedAlertTitleKey = localizedAlertTitleKey;
        this.localizedAlertTitleArguments = (alertTitleArguments != null && alertTitleArguments.length > 0) ? alertTitleArguments : null;

        this.alertTitle = null;

        return this;
    }

    /**
     * <p>Sets a subtitle for the notification. Clears any previously-set localized subtitle key and arguments.</p>
     *
     * <p>By default, no subtitle is included. Requires iOS 10 or newer.</p>
     *
     * @param alertSubtitle the subtitle for this push notification
     *
     * @return a reference to this payload builder
     *
     * @since 0.8.1
     */
    public ApnsPayloadBuilder setAlertSubtitle(final String alertSubtitle) {
        this.alertSubtitle = alertSubtitle;

        this.localizedAlertSubtitleKey = null;
        this.localizedAlertSubtitleArguments = null;

        return this;
    }

    /**
     * <p>Sets the key of the subtitle string in the receiving app's localized string list to be shown for the push
     * notification. Clears any previously-set literal subtitle. The message in the app's string list may optionally
     * have placeholders, which will be populated by values from the given {@code alertSubtitleArguments}.</p>
     *
     * <p>By default, no subtitle is included. Requires iOS 10 or newer.</p>
     *
     * @param localizedAlertSubtitleKey a key to a string in the receiving app's localized string list
     * @param alertSubtitleArguments arguments to populate placeholders in the localized subtitle string; may be
     * {@code null}
     *
     * @return a reference to this payload builder
     *
     * @since 0.8.1
     */
    public ApnsPayloadBuilder setLocalizedAlertSubtitle(final String localizedAlertSubtitleKey, final String... alertSubtitleArguments) {
        this.localizedAlertSubtitleKey = localizedAlertSubtitleKey;
        this.localizedAlertSubtitleArguments = (alertSubtitleArguments != null && alertSubtitleArguments.length > 0) ? alertSubtitleArguments : null;

        this.alertSubtitle = null;

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
     *
     * @return a reference to this payload builder
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
     * @param showActionButton {@code true} to show an action button when the push notification is presented as an alert
     * or {@code false} to show an alert with no action button
     *
     * @return a reference to this payload builder
     */
    public ApnsPayloadBuilder setShowActionButton(final boolean showActionButton) {
        this.showActionButton = showActionButton;
        return this;
    }

    /**
     * <p>Sets the literal text of the action button to be shown for the push notification <em>for Safari push
     * notifications only</em>. Clears any previously-set localized action key. By default, the OS-default label will be
     * used for the action button.</p>
     *
     * @param action the literal label to be shown on the action button for this notification
     *
     * @return a reference to this payload builder
     *
     * @see ApnsPayloadBuilder#setLocalizedActionButtonKey(String)
     * @see ApnsPayloadBuilder#setShowActionButton(boolean)
     *
     * @since 0.8.2
     */
    public ApnsPayloadBuilder setActionButtonLabel(final String action) {
        this.actionButtonLabel = action;
        this.localizedActionButtonKey = null;

        return this;
    }

    /**
     * <p>Sets the key of a string in the receiving app's localized string list to be used as the label of the
     * &quot;action&quot; button if the push notification is displayed as an alert. Clears any previously-set literal
     * action button label. By default, the OS-default label will be used for the action button.</p>
     *
     * @param localizedActionButtonKey a key to a string in the receiving app's localized string list
     *
     * @return a reference to this payload builder
     *
     * @see ApnsPayloadBuilder#setActionButtonLabel(String)
     * @see ApnsPayloadBuilder#setShowActionButton(boolean)
     */
    public ApnsPayloadBuilder setLocalizedActionButtonKey(final String localizedActionButtonKey) {
        this.localizedActionButtonKey = localizedActionButtonKey;
        this.actionButtonLabel = null;

        return this;
    }

    /**
     * <p>Sets the number to display as the badge of the icon of the application that receives the push notification.
     * If the badge number is 0, the badge is removed from the application icon. If {@code null}, the badge is left in
     * its current state. By default, no change is made to the badge.</p>
     *
     * @param badgeNumber the number to display as the badge of application or {@code null} to leave the badge unchanged
     *
     * @return a reference to this payload builder
     */
    public ApnsPayloadBuilder setBadgeNumber(final Integer badgeNumber) {
        this.badgeNumber = badgeNumber;
        return this;
    }

    /**
     * <p>Sets the name of the action category name for interactive remote notifications. According to Apple's
     * documentation, this should be:</p>
     *
     * <blockquote>...a string value that represents the identifier property of the
     * {@code UIMutableUserNotificationCategory} object you created to define custom actions.</blockquote>
     *
     * @param categoryName the action category name
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/SupportingNotificationsinYourApp.html#//apple_ref/doc/uid/TP40008194-CH4-SW26">Configuring
     * Categories and Actionable Notifications</a>
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
     * @param soundFileName
     *            the name of the sound file to play, or {@code null} to send no sound
     *
     * @return a reference to this payload builder
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
     * @return a reference to this payload builder
     *
     * @see <a href=
     *      "https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CreatingtheNotificationPayload.html#//apple_ref/doc/uid/TP40008194-CH10-SW8">Configuring
     *      a Silent Notification</a>
     */
    public ApnsPayloadBuilder setContentAvailable(final boolean contentAvailable) {
        this.contentAvailable = contentAvailable;
        return this;
    }

    /**
     * Sets whether the receiving device may modify the content of the push notification before displaying it. Requires
     * iOS 10 or newer.
     *
     * @param mutableContent {@code true} if the receiving device may modify the push notification before displaying it
     * or {@code false} otherwise
     *
     * @return a reference to this payload builder
     *
     * @see <a href=
     *      "https://developer.apple.com/reference/usernotifications/unnotificationserviceextension">
     *      UNNotificationServiceExtension</a>
     */
    public ApnsPayloadBuilder setMutableContent(final boolean mutableContent) {
        this.mutableContent = mutableContent;
        return this;
    }

    /**
     * <p>Sets the thread ID for this notification. According to to the APNs documentation, the thread ID is:</p>
     *
     * <blockquote>…a string value that represents the app-specific identifier for grouping notifications. The system
     * groups notifications with the same thread identifier together in Notification Center and other system
     * interfaces.</blockquote>
     *
     * <p>By default, no thread ID is included.</p>
     *
     * @param threadId the thread ID for this notification
     *
     * @return a reference to this payload builder
     *
     * @since 0.8.2
     */
    public ApnsPayloadBuilder setThreadId(final String threadId) {
        this.threadId = threadId;
        return this;
    }

    /**
     * <p>Sets the list of arguments to populate placeholders in the {@code urlFormatString} associated with a Safari
     * push notification. Has no effect for non-Safari notifications. According to the Notification Programming Guide
     * for Websites:</p>
     *
     * <blockquote>The {@code url-args} key must be included [for Safari push notifications]. The number of elements in
     * the array must match the number of placeholders in the {@code urlFormatString} value and the order of the
     * placeholders in the URL format string determines the order of the values supplied by the {@code url-args} array.
     * The number of placeholders may be zero, in which case the array should be empty. However, it is common practice
     * to always include at least one argument so that the user is directed to a web page specific to the notification
     * received.</blockquote>
     *
     * @param arguments the arguments with which to populate URL placeholders, which may be an empty list; if
     * {@code null}, the {@code url-args} key is ommitted from the payload entirely
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/NotificationProgrammingGuideForWebsites/PushNotifications/PushNotifications.html#//apple_ref/doc/uid/TP40013225-CH3-SW1">
     *      Notification Programming Guide for Websites - Configuring Safari Push Notifications</a>
     *
     * @since 0.8.2
     */
    public ApnsPayloadBuilder setUrlArguments(final List<String> arguments) {
        return this.setUrlArguments(arguments != null ? arguments.toArray(new String[0]) : null);
    }

    /**
     * <p>Sets the list of arguments to populate placeholders in the {@code urlFormatString} associated with a Safari
     * push notification. Has no effect for non-Safari notifications. According to the Notification Programming Guide
     * for Websites:</p>
     *
     * <blockquote>The {@code url-args} key must be included [for Safari push notifications]. The number of elements in
     * the array must match the number of placeholders in the {@code urlFormatString} value and the order of the
     * placeholders in the URL format string determines the order of the values supplied by the {@code url-args} array.
     * The number of placeholders may be zero, in which case the array should be empty. However, it is common practice
     * to always include at least one argument so that the user is directed to a web page specific to the notification
     * received.</blockquote>
     *
     * @param arguments the arguments with which to populate URL placeholders, which may be an empty array; if
     * {@code null}, the {@code url-args} key is ommitted from the payload entirely
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/NotificationProgrammingGuideForWebsites/PushNotifications/PushNotifications.html#//apple_ref/doc/uid/TP40013225-CH3-SW1">
     *      Notification Programming Guide for Websites - Configuring Safari Push Notifications</a>
     *
     * @since 0.8.2
     */
    public ApnsPayloadBuilder setUrlArguments(final String... arguments) {
        this.urlArguments = arguments;
        return this;
    }

    /**
     * <p>Adds a custom property to the payload. According to Apple's documentation:</p>
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
     *
     * @return a reference to this payload builder
     */
    public ApnsPayloadBuilder addCustomProperty(final String key, final Object value) {
        this.customProperties.put(key, value);
        return this;
    }

    /**
     * <p>Returns a JSON representation of the push notification payload under construction. If the payload length is
     * longer than the default maximum ({@value com.relayrides.pushy.apns.util.ApnsPayloadBuilder#DEFAULT_MAXIMUM_PAYLOAD_SIZE}
     * bytes), the literal alert body will be shortened if possible. If the alert body cannot be shortened or is not
     * present, an {@code IllegalArgumentException} is thrown.</p>
     *
     * @return a JSON representation of the payload under construction (possibly with an abbreviated alert body)
     *
     * @throws IllegalArgumentException if the payload is too large and cannot be compressed by truncating its literal
     * alert message
     */
    public String buildWithDefaultMaximumLength() {
        return this.buildWithMaximumLength(DEFAULT_MAXIMUM_PAYLOAD_SIZE);
    }

    /**
     * <p>Returns a JSON representation of the push notification payload under construction. If the payload length is
     * longer than the given maximum, the literal alert body will be shortened if possible. If the alert body cannot be
     * shortened or is not present, an {@code IllegalArgumentException} is thrown.</p>
     *
     * @param maximumPayloadSize the maximum length of the payload in bytes
     *
     * @return a JSON representation of the payload under construction (possibly with an abbreviated alert body)
     *
     * @throws IllegalArgumentException if the payload is too large and cannot be compressed by truncating its literal
     * alert message
     */
    public String buildWithMaximumLength(final int maximumPayloadSize) {
        final Map<String, Object> payload = new HashMap<>();

        {
            final Map<String, Object> aps = new HashMap<>();

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

            if (this.mutableContent) {
                aps.put(MUTABLE_CONTENT_KEY, 1);
            }

            if (this.threadId != null) {
                aps.put(THREAD_ID_KEY, this.threadId);
            }

            if (this.urlArguments != null) {
                aps.put(URL_ARGS_KEY, this.urlArguments);
            }

            final Map<String, Object> alert = new HashMap<>();
            {
                if (this.alertBody != null) {
                    alert.put(ALERT_BODY_KEY, this.alertBody);
                }

                if (this.alertTitle != null) {
                    alert.put(ALERT_TITLE_KEY, this.alertTitle);
                }

                if (this.alertSubtitle != null) {
                    alert.put(ALERT_SUBTITLE_KEY, this.alertSubtitle);
                }

                if (this.showActionButton) {
                    if (this.localizedActionButtonKey != null) {
                        alert.put(ACTION_LOC_KEY, this.localizedActionButtonKey);
                    }

                    if (this.actionButtonLabel != null) {
                        alert.put(ACTION_KEY, this.actionButtonLabel);
                    }
                } else {
                    // To hide the action button, the key needs to be present, but the value needs to be null
                    alert.put(ACTION_LOC_KEY, null);
                }

                if (this.localizedAlertKey != null) {
                    alert.put(ALERT_LOC_KEY, this.localizedAlertKey);

                    if (this.localizedAlertArguments != null) {
                        alert.put(ALERT_ARGS_KEY, Arrays.asList(this.localizedAlertArguments));
                    }
                }

                if (this.localizedAlertTitleKey != null) {
                    alert.put(ALERT_TITLE_LOC_KEY, this.localizedAlertTitleKey);

                    if (this.localizedAlertTitleArguments != null) {
                        alert.put(ALERT_TITLE_ARGS_KEY, Arrays.asList(this.localizedAlertTitleArguments));
                    }
                }

                if (this.localizedAlertSubtitleKey != null) {
                    alert.put(ALERT_SUBTITLE_LOC_KEY, this.localizedAlertSubtitleKey);

                    if (this.localizedAlertSubtitleArguments != null) {
                        alert.put(ALERT_SUBTITLE_ARGS_KEY, Arrays.asList(this.localizedAlertSubtitleArguments));
                    }
                }

                if (this.launchImageFileName != null) {
                    alert.put(LAUNCH_IMAGE_KEY, this.launchImageFileName);
                }
            }

            if (alert.size() == 1 && alert.containsKey(ALERT_BODY_KEY) && this.preferStringRepresentationForAlerts) {
                aps.put(ALERT_KEY, alert.get(ALERT_BODY_KEY));
            } else if (!alert.isEmpty()) {
                aps.put(ALERT_KEY, alert);
            }

            payload.put(APS_KEY, aps);
        }

        for (final Map.Entry<String, Object> entry : this.customProperties.entrySet()) {
            payload.put(entry.getKey(), entry.getValue());
        }

        this.buffer.reset();
        gson.toJson(payload, this.buffer);

        final String payloadString = this.buffer.toString();
        final int initialPayloadSize = payloadString.getBytes(StandardCharsets.UTF_8).length;

        final String fittedPayloadString;

        if (initialPayloadSize <= maximumPayloadSize) {
            fittedPayloadString = payloadString;
        } else {
            if (this.alertBody != null) {
                this.replaceMessageBody(payload, "");

                this.buffer.reset();
                gson.toJson(payload, this.buffer);

                final int payloadSizeWithEmptyMessage = this.buffer.toString().getBytes(StandardCharsets.UTF_8).length;

                if (payloadSizeWithEmptyMessage >= maximumPayloadSize) {
                    throw new IllegalArgumentException("Payload exceeds maximum size even with an empty message body.");
                }

                final int maximumEscapedMessageBodySize = maximumPayloadSize - payloadSizeWithEmptyMessage -
                        ABBREVIATION_SUBSTRING.getBytes(StandardCharsets.UTF_8).length;

                final String fittedMessageBody = this.alertBody.substring(0,
                        ApnsPayloadBuilder.getLengthOfJsonEscapedUtf8StringFittingSize(this.alertBody, maximumEscapedMessageBodySize));

                this.replaceMessageBody(payload, fittedMessageBody + ABBREVIATION_SUBSTRING);

                this.buffer.reset();
                gson.toJson(payload, this.buffer);

                fittedPayloadString = this.buffer.toString();
            } else {
                throw new IllegalArgumentException(String.format(
                        "Payload size is %d bytes (with a maximum of %d bytes) and cannot be shortened.",
                        initialPayloadSize, maximumPayloadSize));
            }
        }

        return fittedPayloadString;
    }

    private void replaceMessageBody(final Map<String, Object> payload, final String messageBody) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> aps = (Map<String, Object>) payload.get(APS_KEY);
        final Object alert = aps.get(ALERT_KEY);

        if (alert != null) {
            if (alert instanceof String) {
                aps.put(ALERT_KEY, messageBody);
            } else {
                @SuppressWarnings("unchecked")
                final Map<String, Object> alertObject = (Map<String, Object>) alert;

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

    static int getLengthOfJsonEscapedUtf8StringFittingSize(final String string, final int maximumSize) {
        int i = 0;
        int cumulativeSize = 0;

        for (i = 0; i < string.length(); i++) {
            final char c = string.charAt(i);
            final int charSize = getSizeOfJsonEscapedUtf8Character(c);

            if (cumulativeSize + charSize > maximumSize) {
                // The next character would put us over the edge; bail out here.
                break;
            }

            cumulativeSize += charSize;

            if (Character.isHighSurrogate(c)) {
                // Skip the next character
                i++;
            }
        }

        return i;
    }

    static int getSizeOfJsonEscapedUtf8Character(final char c) {
        final int charSize;

        if (c == '"' || c == '\\' || c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t') {
            // Character is backslash-escaped in JSON
            charSize = 2;
        } else if (c <= 0x001F || c == '\u2028' || c == '\u2029') {
            // Character will be represented as an escaped control character
            charSize = 6;
        } else {
            // The character will be represented as an un-escaped UTF8 character
            if (c <= 0x007F) {
                charSize = 1;
            } else if (c <= 0x07FF) {
                charSize = 2;
            } else if (Character.isHighSurrogate(c)) {
                charSize = 4;
            } else {
                charSize = 3;
            }
        }

        return charSize;
    }
}
