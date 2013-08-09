package com.relayrides.pushy.apns;

/**
 * <p>An APNs environment is a set of servers that provide push notification services. Apple provides two environments:
 * one production environment and one &quot;sandbox&quot; environment. Custom environments may be created for
 * development and testing purposes.</p>
 * 
 * <p>APNs environments may optionally require TLS. Both Apple-provided environments require TLS. See Apple's
 * <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ProvisioningDevelopment.html#//apple_ref/doc/uid/TP40008194-CH104-SW1">
 * &quot;Provisioning and Development&quot;</a> for details.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class ApnsEnvironment {
	private final String apnsGatewayHost;
	private final int apnsGatewayPort;
	
	private final String feedbackHost;
	private final int feedbackPort;
	
	private final boolean tlsRequired;
	
	/**
	 * Constructs a new APenvironment with the given host names and ports.
	 * 
	 * @param apnsGatewayHost the host name of the APNs gateway
	 * @param apnsGatewayPort the TCP port for the APNs gateway
	 * @param feedbackHost the host name of the APNs feedback service
	 * @param feedbackPort the TCP port for the APNs feedback service
	 * @param tlsRequired {@code true} if this environment requires TLS or {@code false} otherwise
	 */
	public ApnsEnvironment(final String apnsGatewayHost, final int apnsGatewayPort, final String feedbackHost, final int feedbackPort, final boolean tlsRequired) {
		this.apnsGatewayHost = apnsGatewayHost;
		this.apnsGatewayPort = apnsGatewayPort;
		
		this.feedbackHost = feedbackHost;
		this.feedbackPort = feedbackPort;
		
		this.tlsRequired = tlsRequired;
	}
	
	/**
	 * Returns the host name of the APNs gateway in this environment.
	 * 
	 * @return the host name of the APNs gateway in this environment
	 */
	public String getApnsGatewayHost() {
		return this.apnsGatewayHost;
	}
	
	/**
	 * Returns the TCP port for the APNs gateway in this environment.
	 * 
	 * @return the TCP port for the APNs gateway in this environment
	 */
	public int getApnsGatewayPort() {
		return this.apnsGatewayPort;
	}
	
	/**
	 * Returns the host name of the APNs feedback service in this environment.
	 * 
	 * @return the host name of the APNs feedback service in this environment
	 */
	public String getFeedbackHost() {
		return this.feedbackHost;
	}
	
	/**
	 * Returns the TCP port for the APNs feedback service in this environment.
	 * 
	 * @return the TCP port for the APNs feedback service in this environment
	 */
	public int getFeedbackPort() {
		return this.feedbackPort;
	}
	
	/**
	 * Indicates whether this environment requires TLS.
	 * 
	 * @return {@code true} if this environment requires TLS or {@code false} otherwise
	 */
	public boolean isTlsRequired() {
		return this.tlsRequired;
	}
	
	/**
	 * Returns an APNs environment for connecting to Apple's production servers.
	 * 
	 * @return an APNs environment for connecting to Apple's production servers
	 */
	public static ApnsEnvironment getProductionEnvironment() {
		return new ApnsEnvironment("gateway.push.apple.com", 2195, "feedback.push.apple.com", 2196, true);
	}
	
	/**
	 * Returns an APNs environment for connecting to Apple's sandbox servers.
	 * 
	 * @return an APNs environment for connecting to Apple's sandbox servers
	 */
	public static ApnsEnvironment getSandboxEnvironment() {
		return new ApnsEnvironment("gateway.sandbox.push.apple.com", 2195, "feedback.sandbox.push.apple.com", 2196, true);
	}
}
