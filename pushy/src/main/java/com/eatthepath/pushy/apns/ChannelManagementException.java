package com.eatthepath.pushy.apns;

import java.util.UUID;

/**
 * A channel management exception indicates that a request to interact with a broadcast push notification channel was
 * received, acknowledged, and ultimately rejected by the APNs server.
 *
 * @see <a href="https://developer.apple.com/documentation/usernotifications/handling-error-responses-from-apns">Handling error responses from Apple Push Notification service</a>
 */
public class ChannelManagementException extends RuntimeException {

  private final int status;
  private final UUID apnsRequestId;
  private final String reason;

  /**
   * Constructs a new channel management exception with the given status code, request ID, and rejection reason.
   *
   * @param status the HTTP status code returned by the APNs server
   * @param apnsRequestId the unique identifier for the request that was rejected
   * @param reason an APNs-specific rejection reason provided by the APNs server
   */
  public ChannelManagementException(final int status, final UUID apnsRequestId, final String reason) {
    this.status = status;
    this.apnsRequestId = apnsRequestId;
    this.reason = reason;
  }

  /**
   * Returns the HTTP status code returned by the APNs server
   *
   * @return the HTTP status code returned by the APNs server
   */
  public int getStatus() {
    return status;
  }

  /**
   * Returns a unique identifier for the request that was rejected.
   *
   * @return a unique identifier for the request that was rejected
   */
  public UUID getApnsRequestId() {
    return apnsRequestId;
  }

  /**
   * Returns an APNs-specific rejection reason provided by the APNs server.
   *
   * @return an APNs-specific rejection reason provided by the APNs server
   */
  public String getReason() {
    return reason;
  }
}
