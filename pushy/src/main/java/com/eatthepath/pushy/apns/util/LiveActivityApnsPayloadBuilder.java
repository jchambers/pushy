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

import java.util.*;

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
    private String alertTitle = null;
    private String soundFileName = null;
    private LiveActivityEvent event = null;
    private Long timestamp = null;
    private final HashMap<String, Object> contentState = new HashMap<>();


    private static final String APS_KEY = "aps";
    private static final String ALERT_KEY = "alert";
    private static final String SOUND_KEY = "sound";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String EVENT_KEY = "event";
    private static final String CONTENT_STATE_KEY = "content-state";
    private static final String ALERT_TITLE_KEY = "title";
    private static final String ALERT_SUBTITLE_KEY = "subtitle";
    private static final String ALERT_BODY_KEY = "body";

    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * The name of the iOS default push notification sound.
     *
     * @see LiveActivityApnsPayloadBuilder#setAlertSound(String)
     */
    public static final String DEFAULT_SOUND_FILENAME = "default";

    /**
     * The name of the value for silent Live Notification alert.
     *
     * @see LiveActivityApnsPayloadBuilder#setAlertSound(String)
     */
    public static final String SILENT_SOUND_FILENAME = "";

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
     * <p>Sets the name of the sound file to play when the push notification is received. According to Apple's
     * documentation, the sound filename should be:</p>
     *
     * <blockquote>The name of a sound file in your app’s main bundle or in the {@code Library/Sounds} folder of your
     * app’s container directory.</blockquote>
     *
     * <p>By default, no sound is included in the push notification. However, the Live Activity will play the default
     * alert tone if no sound is not set but alert title and alert body are set. In that case, you can use
     * {@code LiveActivityApnsPayloadBuilder#SILENT_SOUND_FILENAME} to disable sound.</p>
     *
     * @param soundFileName the name of the sound file to play, or {@code null} to send no sound
     *
     * @return a reference to this payload builder
     *
     * @see LiveActivityApnsPayloadBuilder#DEFAULT_SOUND_FILENAME
     * @see LiveActivityApnsPayloadBuilder#SILENT_SOUND_FILENAME
     *
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
    public LiveActivityApnsPayloadBuilder setTimestamp(final Long timestamp) {
        this.timestamp = timestamp;
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
                aps.put(TIMESTAMP_KEY, this.timestamp);
            }

            if (this.event != null) {
                aps.put(EVENT_KEY, this.event.getValue());
            }

            if (!this.contentState.isEmpty()) {
                aps.put(CONTENT_STATE_KEY, this.contentState);
            }

            final Map<String, Object> alert = new HashMap<>();
            {
                if (this.alertBody != null) {
                    alert.put(ALERT_BODY_KEY, this.alertBody);
                }

                if (this.alertTitle != null) {
                    alert.put(ALERT_TITLE_KEY, this.alertTitle);
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
