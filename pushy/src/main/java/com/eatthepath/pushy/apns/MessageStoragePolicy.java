package com.eatthepath.pushy.apns;

import java.util.UUID;

/**
 * An enumeration of message storage policies for APNs broadcast notification channels.
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/sending-broadcast-push-notification-requests-to-apns">Sending broadcast push notification requests to APNs</a>
 * @see ApnsClient#createChannel(String, MessageStoragePolicy, UUID)
 * @see ApnsClient#getChannelConfiguration(String, String, UUID)
 * @see ApnsPushNotification#getExpiration()
 *
 * @since 0.16
 */
public enum MessageStoragePolicy {

  /**
   * Indicates that a broadcast notification channel should not store and forward notifications if they cannot be
   * delivered immediately. As the documentation notes:
   *
   * <blockquote>Providing a nonzero expiration for a channel created with the No Message Stored storage policy results
   * in message rejection. </blockquote>
   */
  NO_MESSAGE_STORED(0),

  /**
   * Indicates that a broadcast notification channel may attempt to store and forward notifications if they cannot be
   * delivered immediately. As the broadcast notification documentation explains:
   *
   * <blockquote>As a best-effort service, APNs may reorder notifications you send on the same channel. If APNs can’t
   * deliver a notification immediately, it may store the notification based on the channel’s message storage policy
   * specified during channel creation. Notifications with Medium and Low apns-priority might get grouped and delivered
   * in bursts to the person’s device. APNs may also throttle your notifications and, in some cases, not deliver them.
   * The exact behavior is determined by the way the person interacts with your application and the power state of the
   * device.</blockquote>
   */
  MOST_RECENT_MESSAGE_STORED(1);

  private final int code;

  MessageStoragePolicy(final int code) {
    this.code = code;
  }

  int getCode() {
    return this.code;
  }

  static MessageStoragePolicy getFromCode(final int code) {
    for (final MessageStoragePolicy policy : MessageStoragePolicy.values()) {
      if (policy.getCode() == code) {
        return policy;
      }
    }

    throw new IllegalArgumentException(String.format("No message storage policy found with code %d", code));
  }
}
