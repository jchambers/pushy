package com.eatthepath.pushy.apns;

/**
 * A response to a "create channel" request sent to the APNs broadcast channel management system.
 *
 * @since 0.16
 */
public interface CreateChannelResponse extends ChannelManagementResponse {

  /**
   * Returns the base64-encoded representation of the newly-created channel ID.
   *
   * @return the base64-encoded representation of the newly-created channel ID
   */
  String getChannelId();
}
