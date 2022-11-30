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

package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.LiveActivityEvent;

import java.time.Instant;
import java.util.Map;

/**
 * An enumeration of push notification display types. Note that push notification display types are required in iOS 13
 * and later and watchOS 6 and later, but are ignored under earlier versions of either operating system.
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/setting_up_a_remote_notification_server/sending_notification_requests_to_apns">Sending
 * Notification Requests to APNs</a>
 *
 * @since 0.13.9
 */
public enum PushType {

    /**
     * <p>Indicates that a push notification is expected to display an alert, play a sound, or badges the receiving
     * apps' icon. According to Apple's documentation:</p>
     *
     * <blockquote>Use the {@code alert} push type for notifications that trigger a user interaction—for example, an
     * alert, badge, or sound. If you set this push type, the {@code apns-topic} header field must use your app’s bundle
     * ID as the topic… The {@code alert} push type is required on watchOS 6 and later. It is recommended on macOS, iOS,
     * tvOS, and iPadOS.</blockquote>
     *
     * @since 0.13.9
     */
    ALERT("alert"),

    /**
     * <p>Indicates that a push notification is not expected to interact with the user on the receiving device.
     * According to Apple's documentation:</p>
     *
     * <blockquote>Use the background push type for notifications that deliver content in the background, and don’t
     * trigger any user interactions. If you set this push type, the apns-topic header field must use your app’s bundle
     * ID as the topic. The background push type is required on watchOS 6 and later. It is recommended on macOS, iOS,
     * tvOS, and iPadOS.</blockquote>
     *
     * @since 0.13.9
     */
    BACKGROUND("background"),

    /**
     * <p>Indicates that a push notification is expected to activate the client for handling VoIP flow. According to
     * Apple's documentation:</p>
     *
     * <blockquote>Use the {@code voip} push type for notifications that provide information about an incoming
     * Voice-over-IP (VoIP) call… If you set this push type, the {@code apns-topic} header field must use your app’s
     * bundle ID with {@code .voip} appended to the end. If you’re using certificate-based authentication, you must also
     * register the certificate for VoIP services. The topic is then part of the 1.2.840.113635.100.6.3.4 or
     * 1.2.840.113635.100.6.3.6 extension. The {@code voip} push type is not available on watchOS. It is recommended on
     * macOS, iOS, tvOS, and iPadOS.</blockquote>
     *
     * @since 0.13.10
     */
    VOIP("voip"),

    /**
     * <p>Indicates that a push notification is intended to provide an update for a watchOS app complication. According
     * to Apple's documentation:</p>
     *
     * <blockquote>Use the {@code complication} push type for notifications that contain update information for a
     * watchOS app’s complications… If you set this push type, the {@code apns-topic} header field must use your app’s
     * bundle ID with {@code .complication} appended to the end. If you’re using certificate-based authentication, you
     * must also register the certificate for WatchKit services. The topic is then part of the 1.2.840.113635.100.6.3.6
     * extension. The {@code complication} push type is recommended for watchOS and iOS. It is not available on macOS,
     * tvOS, and iPadOS.</blockquote>
     *
     * @since 0.13.10
     */
    COMPLICATION("complication"),

    /**
     * <p>Indicates that a push notification is intended to update a File Provider extension. According to Apple's
     * documentation:</p>
     *
     * <blockquote>Use the {@code fileprovider} push type to signal changes to a File Provider extension. If you set
     * this push type, the apns-topic header field must use your app’s bundle ID with {@code .pushkit.fileprovider}
     * appended to the end… The fileprovider push type is not available on watchOS. It is recommended on macOS, iOS,
     * tvOS, and iPadOS.</blockquote>
     *
     * @since 0.13.10
     */
    FILEPROVIDER("fileprovider"),

    /**
     * <p>Indicates that a push notification is intended to cause the receiving device to contact its mobile device
     * management (MDM) server. According to Apple's documentation:</p>
     *
     * <blockquote>Use the {@code mdm} push type for notifications that tell managed devices to contact the MDM server.
     * If you set this push type, you must use the topic from the UID attribute in the subject of your MDM push
     * certificate… The mdm push type is not available on watchOS. It is recommended on macOS, iOS, tvOS, and
     * iPadOS.</blockquote>
     *
     * @since 0.13.10
     */
    MDM("mdm"),
    
    /**
     * <p>Indicates that a push notification is intended to query the destination device's location and activate a
     * <a href="https://developer.apple.com/documentation/corelocation/cllocationmanager/creating_a_location_push_service_extension">Location
     * Push Service Extension</a> on the destination device. According to Apple's documentation:</p>
     *
     * <blockquote><p>Use the {@code location} push type for notifications that request a user’s location. If you set
     * this push type, the {@code apns-topic} header field must use your app’s bundle ID with {@code .location-query}
     * appended to the end.</p>
     *
     * <p>The location push type is recommended for iOS and iPadOS. It isn’t available on macOS, tvOS, and watchOS.</p>
     *
     * <p>If the location query requires an immediate response from the Location Push Service Extension, set
     * notification {@code apns-priority} to 10 [{@link DeliveryPriority#IMMEDIATE}]; otherwise, use 5
     * [{@link DeliveryPriority#CONSERVE_POWER}].</p>
     *
     * <p>The location push type supports only token-based authentication.</p></blockquote>
     *
     * @since 0.15.1
     *
     * @see ApnsClientBuilder#setSigningKey(ApnsSigningKey)
     * @see DeliveryPriority
     */
    LOCATION("location"),

    /**
     * Indicates that a push notification is intended to update a running Live Activity. Note that Live Activity updates
     * must be sent to a specific topic ({@code [base bundle ID].push-type.liveactivity}) and include specific payload
     * keys (see {@link ApnsPayloadBuilder#setContentState(Map)}, {@link ApnsPayloadBuilder#setEvent(LiveActivityEvent)},
     * {@link ApnsPayloadBuilder#setDismissalDate(Instant)}, and {@link ApnsPayloadBuilder#setTimestamp(Instant)}).
     *
     * @since 0.15.2
     *
     * @see <a href="https://developer.apple.com/documentation/activitykit/update-and-end-your-live-activity-with-remote-push-notifications">
     *     Updating and ending your Live Activity with remote push notifications</a>
     */
    LIVE_ACTIVITY("liveactivity"),
        /**
     * Indicates that a push notification is intended to update a running push to talk. Note that push to talk
     * must be sent to a specific topic ({@code [base bundle ID].push-type.pushtotalk}) and include specific payload
     * keys (see {@link ApnsPayloadBuilder#setContentState(Map)}, {@link ApnsPayloadBuilder#setEvent(LiveActivityEvent)},
     * {@link ApnsPayloadBuilder#setDismissalDate(Instant)}, and {@link ApnsPayloadBuilder#setTimestamp(Instant)}).
     *
     * @since 0.15.3
     *
     * @see <a href="https://developer.apple.com/documentation/pushtotalk/creating_a_push_to_talk_app">
     *     Creating a Push to Talk app</a>
     */
    PUSHTOTALK("pushtotalk");

    private final String headerValue;

    PushType(final String headerValue) {
        this.headerValue = headerValue;
    }

    public String getHeaderValue() {
        return this.headerValue;
    }

    public static PushType getFromHeaderValue(final CharSequence headerValue) {
        for (final PushType pushType : PushType.values()) {
            if (pushType.headerValue.contentEquals(headerValue)) {
                return pushType;
            }
        }

        throw new IllegalArgumentException("No push type found for header value: " + headerValue);
    }
}
