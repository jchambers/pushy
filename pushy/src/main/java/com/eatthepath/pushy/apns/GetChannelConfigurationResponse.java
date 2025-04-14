package com.eatthepath.pushy.apns;

/**
 * A response to a "get channel configuration" request sent to the APNs broadcast notification channel management
 * system.
 *
 * @since 0.16
 */
public interface GetChannelConfigurationResponse extends ChannelManagementResponse {

  /**
   * Returns the message storage policy for the channel named in the original request.
   *
   * @return the message storage policy for the channel named in the original request
   */
  MessageStoragePolicy getMessageStoragePolicy();
}
