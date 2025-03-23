package com.eatthepath.pushy.apns;

import java.util.List;
import java.util.Optional;

public interface GetChannelIdsResponse extends ChannelManagementResponse {

  List<String> getChannelIds();
}
