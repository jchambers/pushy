package com.relayrides.pushy;

import java.util.Arrays;
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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((expiration == null) ? 0 : expiration.hashCode());
		result = prime * result + Arrays.hashCode(token);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ExpiredToken other = (ExpiredToken) obj;
		if (expiration == null) {
			if (other.expiration != null)
				return false;
		} else if (!expiration.equals(other.expiration))
			return false;
		if (!Arrays.equals(token, other.token))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ExpiredToken [token=" + Arrays.toString(token)
				+ ", expiration=" + expiration + "]";
	}
}
