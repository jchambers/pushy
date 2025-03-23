package com.eatthepath.pushy.apns;

import java.util.Optional;

public interface CreateChannelResponse extends ChannelManagementResponse {

  String getChannelId();
}
