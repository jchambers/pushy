package com.relayrides.pushy.apns;

import java.util.Collection;

public interface ExpiredTokenListener {
	void handleExpiredTokens(Collection<ExpiredToken> expiredTokens);
}
