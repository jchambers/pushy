package com.eatthepath.pushy.apns;

import java.util.UUID;

/**
 * A response from the APNs broadcast channel management system.
 *
 * @since 0.16
 */
public interface ChannelManagementResponse {

  /**
   * Returns the unique identifier (which may be assigned by the caller or by the server if the caller does not provide
   * a unique identifier) for the original request.
   *
   * @return the unique identifier for the original request
   */
  UUID getRequestId();

  /**
   * Returns the HTTP status code returned by the APNs server.
   *
   * @return the HTTP status code returned by the APNs server
   */
  int getStatus();
}
