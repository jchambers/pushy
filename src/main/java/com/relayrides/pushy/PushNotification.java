package com.relayrides.pushy;

import java.util.Date;

public interface PushNotification {
	byte[] getDestinationToken();
	byte[] getPayload();
	Date getExpiration();
}
