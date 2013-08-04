package com.relayrides.pushy.apns;

public class ApnsEnvironment {
	private final String host;
	private final int port;
	
	private final boolean tlsRequired;
	
	public ApnsEnvironment(final String host, final int port, final boolean tlsRequired) {
		this.host = host;
		this.port = port;
		
		this.tlsRequired = tlsRequired;
	}
	
	public String getHost() {
		return this.host;
	}
	
	public int getPort() {
		return this.port;
	}
	
	public boolean isTlsRequired() {
		return this.tlsRequired;
	}
	
	public static ApnsEnvironment getProductionEnvironment() {
		return new ApnsEnvironment("gateway.push.apple.com", 2195, true);
	}
	
	public static ApnsEnvironment getSandboxEnvironment() {
		return new ApnsEnvironment("gateway.sandbox.push.apple.com", 2195, true);
	}
}
