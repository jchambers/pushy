package com.eatthepath.pushy.apns;

import java.util.List;
import java.util.Optional;

/**
 * A response to a "list all channel IDs" request sent to the APNS broadcast notification channel management system.
 *
 * @since 0.16
 */
public interface GetChannelIdsResponse extends ChannelManagementResponse {

  /**
   * Returns a list of all active channel IDs for the bundle ID named in the original request.
   *
   * @return a list of all active channel IDs for the bundle ID named in the original request
   */
  List<String> getChannelIds();
}
