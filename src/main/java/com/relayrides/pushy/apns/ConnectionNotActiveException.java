package com.relayrides.pushy.apns;

class ConnectionNotActiveException extends Exception {

	private static final long serialVersionUID = 1L;

	public ConnectionNotActiveException(final String message) {
		super(message);
	}
}
