package com.relayrides.pushy;

public class ApnsEnvironment {
	private final String apnsHost;
	private final int apnsPort;
	
	private final String feedbackHost;
	private final int feedbackPort;
	
	private final boolean tlsRequired;
	
	public ApnsEnvironment(final String apnsHost, final int apnsPort, final String feedbackHost, final int feedbackPort, final boolean tlsRequired) {
		this.apnsHost = apnsHost;
		this.apnsPort = apnsPort;
		
		this.feedbackHost = feedbackHost;
		this.feedbackPort = feedbackPort;
		
		this.tlsRequired = tlsRequired;
	}
	
	public String getApnsHost() {
		return this.apnsHost;
	}
	
	public int getApnsPort() {
		return this.apnsPort;
	}
	
	public String getFeedbackHost() {
		return this.feedbackHost;
	}
	
	public int getFeedbackPort() {
		return this.feedbackPort;
	}
	
	public boolean isTlsRequired() {
		return this.tlsRequired;
	}
	
	public static ApnsEnvironment getProductionEnvironment() {
		return new ApnsEnvironment("gateway.push.apple.com", 2195, "feedback.push.apple.com", 2196, true);
	}
	
	public static ApnsEnvironment getSandboxEnvironment() {
		return new ApnsEnvironment("gateway.sandbox.push.apple.com", 2195, "feedback.sandbox.push.apple.com", 2196, true);
	}
}
