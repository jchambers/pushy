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

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>A base utility class for constructing JSON payloads suitable for inclusion in APNs push notifications for Live
 * Activities. Payload builders are reusable, but are <em>not</em> thread-safe.</p>
 *
 * @see <a href="https://developer.apple.com/documentation/activitykit/update-and-end-your-live-activity-with-remote-push-notifications">Updating and ending your Live Activity with remote push notifications</a>
 *
 * @since 0.15.2
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public abstract class LiveActivityApnsPayloadBuilder {

    private String alertBody = null;
    private String localizedAlertKey = null;
    private String[] localizedAlertArguments = null;
    private String alertTitle = null;
    private String localizedAlertTitleKey = null;
    private String[] localizedAlertTitleArguments = null;
    private String soundFileName = null;
    private LiveActivityEvent event = null;
    private Instant timestamp = null;
    private Instant dismissalDate = null;
    private final HashMap<String, Object> contentState = new HashMap<>();


    private static final String APS_KEY = "aps";
    private static final String ALERT_KEY = "alert";
    private static final String SOUND_KEY = "sound";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String DISMISSAL_DATE_KEY = "dismissal-date";
    private static final String EVENT_KEY = "event";
    private static final String CONTENT_STATE_KEY = "content-state";
    private static final String ALERT_TITLE_KEY = "title";
    private static final String ALERT_TITLE_LOC_KEY = "title-loc-key";
    private static final String ALERT_TITLE_ARGS_KEY = "title-loc-args";
    private static final String ALERT_BODY_KEY = "body";
    private static final String ALERT_LOC_KEY = "loc-key";
    private static final String ALERT_ARGS_KEY = "loc-args";

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * The name of the iOS default push notification sound.
     *
     * @see LiveActivityApnsPayloadBuilder#setAlertSound(String)
     */
    public static final String DEFAULT_SOUND_FILENAME = "default";

    /**
     * Sets the literal text of the alert message. The expanded view of the Live Activity will be shown for a few
     * seconds if an alert body and alert title are sent. It is unclear if the actual text is used anywhere.
     *
     * @param alertBody the literal message to be shown for this push notification
     *
     * @return a reference to this payload builder
     */
    public LiveActivityApnsPayloadBuilder setAlertBody(final String alertBody) {
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
     *
     * @return a reference to this payload builder
     *
     * @see LiveActivityApnsPayloadBuilder#setAlertBody(String)
     */
    public LiveActivityApnsPayloadBuilder setLocalizedAlertMessage(
            final String localizedAlertKey,
            final String... alertArguments
    ) {
        this.localizedAlertKey = localizedAlertKey;
        this.localizedAlertArguments = (alertArguments != null && alertArguments.length > 0) ? alertArguments : null;

        return this;
    }

    /**
     * <p>Sets a short description of the notification purpose. The expanded view of the Live Activity will be
     * shown for a few seconds if an alert body and alert title are sent. It is unclear if the actual text is
     * used anywhere.
     *
     * <p>By default, no title is included.</p>
     *
     * @param alertTitle the description to be shown for this push notification
     *
     * @return a reference to this payload builder
     *
     */
    public LiveActivityApnsPayloadBuilder setAlertTitle(final String alertTitle) {
        this.alertTitle = alertTitle;
        return this;
    }

    /**
     * <p>Sets the key of the title string in the receiving app's localized string list to be shown for the push
     * notification. The message in the app's string list may optionally have placeholders, which will be populated by
     * values from the given {@code alertArguments}.</p>
     *
     * @param localizedAlertTitleKey a key to a string in the receiving app's localized string list
     * @param alertTitleArguments arguments to populate placeholders in the localized alert string; may be {@code null}
     *
     * @return a reference to this payload builder
     *
     * @see LiveActivityApnsPayloadBuilder#setAlertTitle(String)
     */
    public LiveActivityApnsPayloadBuilder setLocalizedAlertTitle(
            final String localizedAlertTitleKey,
            final String... alertTitleArguments
    ) {
        this.localizedAlertTitleKey = localizedAlertTitleKey;
        this.localizedAlertTitleArguments = (alertTitleArguments != null && alertTitleArguments.length > 0) ? alertTitleArguments : null;

        return this;
    }

    /**
     * <p>Sets the name of the sound file to play when the push notification is received. According to Apple's
     * documentation, the sound filename should be:</p>
     *
     * <blockquote>The name of a sound file in your app’s main bundle or in the {@code Library/Sounds} folder of your
     * app’s container directory.</blockquote>
     *
     * <p>By default, no sound is included in the push notification.</p>
     *
     * @param soundFileName the name of the sound file to play, or {@code null} to send no sound
     *
     * @return a reference to this payload builder
     *
     * @see LiveActivityApnsPayloadBuilder#DEFAULT_SOUND_FILENAME
     */
    public LiveActivityApnsPayloadBuilder setAlertSound(final String soundFileName) {
        this.soundFileName = soundFileName;
        return this;
    }

    /**
     * <p>Adds a custom property inside the content-state payload for Live Activities.</p>
     *
     * <p>The precise strategy for serializing the values of custom properties is defined by the specific
     * {@code ApnsPayloadBuilder} implementation.</p>
     *
     * @param key the key of the custom property in the content-state object
     * @param value the value of the custom property
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/documentation/activitykit/update-and-end-your-live-activity-with-remote-push-notifications">
     *      Updating and ending your Live Activity with remote push notifications</a>
     */
    public LiveActivityApnsPayloadBuilder addContentStateProperty(final String key, final Object value) {
        this.contentState.put(key, value);
        return this;
    }

    /**
     * <p>Sets whether the notification payload will be used for updating the Live Activity
     * or for ending it.</p>
     *
     * @param event {@code LiveActivityEvent.UPDATE} to update the Live Activity or
     * {@code LiveActivityEvent.END} to end it.
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/documentation/activitykit/update-and-end-your-live-activity-with-remote-push-notifications">
     *      Updating and ending your Live Activity with remote push notifications</a>
     */
    public LiveActivityApnsPayloadBuilder setEvent(final LiveActivityEvent event) {
        this.event = event;
        return this;
    }

    /**
     * <p>Sets a timestamp for the push notification payload. The timestamp is used to discard older
     * push notifications</p>
     *
     *  @param timestamp Timestamp
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/documentation/activitykit/update-and-end-your-live-activity-with-remote-push-notifications">
     *      Updating and ending your Live Activity with remote push notifications</a>
     */
    public LiveActivityApnsPayloadBuilder setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * <p>Sets a timestamp for the push notification payload. The timestamp is used to discard older
     * push notifications. According to Apple's documentation:</p>
     *
     * <blockquote>When you end a Live Activity, by default the Live Activity appears on the Lock Screen for up to
     * four hours after it ends to allow the user to glance at their phone to see the latest information. To dismiss
     * the Live Activity from the Lock Screen immediately after it ends, provide a date for "dismissal-date" that’s
     * in the past. Alternatively, provide a date within a four-hour window to set a custom dismissal date.</blockquote>
     *
     *  @param dismissalDate Date when the Live Activity will be dismissed
     *
     * @return a reference to this payload builder
     *
     * @see <a href="https://developer.apple.com/documentation/activitykit/update-and-end-your-live-activity-with-remote-push-notifications">
     *      Updating and ending your Live Activity with remote push notifications</a>
     */
    public LiveActivityApnsPayloadBuilder setDismissalDate(final Instant dismissalDate) {
        this.dismissalDate = dismissalDate;
        return this;
    }

    /**
     * Returns a map representing the push notification payload under construction. Subclasses will generally serialize
     * this map as a JSON string to produce a push notification payload.
     *
     * @return a map representing the push notification payload under construction
     *
     * @since 0.14.0
     */
    protected Map<String, Object> buildPayloadMap() {
        final Map<String, Object> payload = new HashMap<>();

        {
            final Map<String, Object> aps = new HashMap<>();

            if (this.timestamp != null) {
                aps.put(TIMESTAMP_KEY, this.timestamp.getEpochSecond());
            }

            if (this.event != null) {
                aps.put(EVENT_KEY, this.event.getValue());
            }

            if (!this.contentState.isEmpty()) {
                aps.put(CONTENT_STATE_KEY, this.contentState);
            }

            if (this.dismissalDate != null) {
                aps.put(DISMISSAL_DATE_KEY, this.dismissalDate.getEpochSecond());
            }

            final Map<String, Object> alert = new HashMap<>();
            {
                if (this.alertBody != null) {
                    alert.put(ALERT_BODY_KEY, this.alertBody);
                }

                if (this.alertTitle != null) {
                    alert.put(ALERT_TITLE_KEY, this.alertTitle);
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

                if (this.soundFileName != null) {
                    alert.put(SOUND_KEY, this.soundFileName);
                }
            }

            if (!alert.isEmpty()) {
                aps.put(ALERT_KEY, alert);
            }

            payload.put(APS_KEY, aps);
        }

        return payload;
    }

    /**
     * Returns a JSON representation of the push notification payload under construction.
     *
     * @return a JSON representation of the payload under construction
     *
     * @see #buildPayloadMap()
     *
     * @since 0.15.2
     */
    public abstract String build();
}
