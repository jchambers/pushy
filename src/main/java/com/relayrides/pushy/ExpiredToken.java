package com.relayrides.pushy;

import java.util.Date;

public class ExpiredToken {
	private final byte[] token;
	private final Date expiration;
	
	public ExpiredToken(final byte[] token, final Date expiration) {
		this.token = token;
		this.expiration = expiration;
	}
	
	public byte[] getToken() {
		return this.token;
	}
	
	public Date getExpiration() {
		return this.expiration;
	}
}
