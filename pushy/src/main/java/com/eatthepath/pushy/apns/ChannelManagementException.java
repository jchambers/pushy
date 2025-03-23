package com.eatthepath.pushy.apns;

import java.util.UUID;

public class ChannelManagementException extends RuntimeException {

  private final int status;
  private final UUID apnsRequestId;

  public ChannelManagementException(final int status, final UUID apnsRequestId) {
    this(status, apnsRequestId, null);
  }

  public ChannelManagementException(final int status, final UUID apnsRequestId, final Throwable cause) {
    super(cause);

    this.status = status;
    this.apnsRequestId = apnsRequestId;
  }

  public int getStatus() {
    return status;
  }

  public UUID getApnsRequestId() {
    return apnsRequestId;
  }
}
