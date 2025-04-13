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

import java.util.Optional;
import java.util.UUID;

public interface BroadcastPushNotificationResponse<T extends ApnsBroadcastPushNotification> {

  /**
   * Returns the original broadcast push notification ent to the APNs server.
   *
   * @return the original broadcast push notification ent to the APNs server
   */
  T getBroadcastPushNotification();

  /**
   * Indicates whether the broadcast push notification was accepted by the APNs server.
   *
   * @return {@code true} if the broadcast push notification was accepted or {@code false} if it was rejected
   */
  boolean isAccepted();

  /**
   * Returns the ID assigned to this push notification by the APNs server.
   *
   * @return the ID assigned to this push notification by the APNs server
   */
  UUID getApnsRequestId();

  /**
   * Returns a unique identifier assigned by the APNs server. Note that this identifier is distinct from the identifier
   * returned by {@link #getApnsRequestId()}. As the documentation notes:
   *
   * <blockquote>Specify a unique identifier when raising troubleshooting requests with Apple.</blockquote>
   *
   * @return the unique identifier assigned by the APNs server
   */
  UUID getApnsUniqueId();

  /**
   * Returns the HTTP status code reported by the APNs server.
   *
   * @return the HTTP status code reported by the APNs server
   */
  int getStatusCode();

  /**
   * Returns the reason for rejection reported by the APNs gateway. If the notification was accepted, the rejection
   * reason will be {@code null}.
   *
   * @return the reason for rejection reported by the APNs gateway, or empty if the notification was not rejected
   */
  Optional<String> getRejectionReason();
}
