/*
 * Copyright (c) 2025 Jon Chambers
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

import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * <p>A broadcast push notification that can be sent through the Apple Push Notification service (APNs). Broadcast push
 * notifications are delivered to a channel, which may have multiple individual devices as subscribers.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/sending-broadcast-push-notification-requests-to-apns">Sending broadcast push notification requests to APNs</a>
 *
 * @see ApnsClient#createChannel(String, MessageStoragePolicy, UUID) 
 * @see ApnsPayloadBuilder
 *
 * @since 0.16
 */
public interface ApnsBroadcastPushNotification {

  /**
   * Returns the base64-encoded channel ID to which to publish this notification.
   *
   * @return the base64-encoded channel ID to which to publish this notification
   * 
   * @see ApnsClient#createChannel(String, MessageStoragePolicy, UUID) 
   */
  String getChannelId();

  /**
   * Returns the JSON-encoded payload for this push notification.
   *
   * @return the JSON-encoded payload for this push notification
   */
  String getPayload();

  /**
   * Returns the instant at which this notification expires. As the APNs documentation explains:
   *
   * <blockquote>If the value is nonzero, APNs stores the notification and attempts delivery at least once, repeating as
   * necessary until the specified date. If the value is 0, APNs attempts to deliver the notification only once and
   * doesn’t store it. Providing a nonzero expiration for a channel created with the No Message Stored storage policy
   * results in message rejection.
   * <p>
   * A single APNs attempt may involve retries over multiple network interfaces and connections of the destination
   * device. These retries often span some time period, depending on network characteristics. Additionally, a push
   * notification may take some time on the network after APNs sends it to the device. APNs makes best efforts to honor
   * the expiry date without any guarantee. If the value is nonzero, the notification may deliver after the specified
   * timestamp. If the value is 0, the notification may deliver with some delay.</blockquote>
   *
   * @return the instant at which the notification expires; may be {@code null}, in which case a value of 0 (i.e.
   * attempt delivery only once) is transmitted to the APNs server
   */
  Instant getExpiration();

  /**
   * Returns the priority with which this push notification should be sent to the receiving device. If {@code null},
   * an immediate delivery priority is assumed.
   *
   * @return the priority with which this push notification should be sent to the receiving device
   */
  DeliveryPriority getPriority();

  /**
   * Returns the canonical identifier for this push notification. The APNs server will include the given identifier in
   * all responses related to this push notification. If no identifier is provided, the server will assign a unique
   * identifier automatically.
   *
   * @return a unique identifier for this notification; may be {@code null}, in which case the APNs server will assign
   * an identifier automatically
   */
  UUID getApnsRequestId();
}
