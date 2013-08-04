package com.relayrides.pushy;

import java.util.Date;

public interface ApnsPushNotification {
	byte[] getTokenBytes();
	byte[] getPayloadBytes();
	Date getExpiration();
}
