package com.eatthepath.pushy.apns;

import java.util.UUID;

public interface ChannelManagementResponse {

  UUID getRequestId();

  int getStatus();
}
