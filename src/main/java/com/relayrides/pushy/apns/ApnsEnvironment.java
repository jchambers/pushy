/* Copyright (c) 2013 RelayRides
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.relayrides.pushy.apns;

/**
 * <p>An APNs environment is a set of servers that provide push notification services. Apple provides two environments:
 * one production environment and one &quot;sandbox&quot; environment. Custom environments may be created for
 * development and testing purposes. For instructions on getting credentials for communicating with Apple's gateway, see
 * <a href="http://developer.apple.com/library/ios/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/Chapters/ProvisioningDevelopment.html#//apple_ref/doc/uid/TP40008194-CH104-SW1">
 * &quot;Provisioning and Development&quot;</a>.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class ApnsEnvironment {
	private final String apnsGatewayHost;
	private final int apnsGatewayPort;

	private final String feedbackHost;
	private final int feedbackPort;

	/**
	 * Constructs a new APenvironment with the given host names and ports.
	 * 
	 * @param apnsGatewayHost the host name of the APNs gateway
	 * @param apnsGatewayPort the TCP port for the APNs gateway
	 * @param feedbackHost the host name of the APNs feedback service
	 * @param feedbackPort the TCP port for the APNs feedback service
	 */
	public ApnsEnvironment(final String apnsGatewayHost, final int apnsGatewayPort, final String feedbackHost, final int feedbackPort) {
		this.apnsGatewayHost = apnsGatewayHost;
		this.apnsGatewayPort = apnsGatewayPort;

		this.feedbackHost = feedbackHost;
		this.feedbackPort = feedbackPort;
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
	 * Returns an APNs environment for connecting to Apple's production servers.
	 * 
	 * @return an APNs environment for connecting to Apple's production servers
	 */
	public static ApnsEnvironment getProductionEnvironment() {
		return new ApnsEnvironment("gateway.push.apple.com", 2195, "feedback.push.apple.com", 2196);
	}

	/**
	 * Returns an APNs environment for connecting to Apple's sandbox servers.
	 * 
	 * @return an APNs environment for connecting to Apple's sandbox servers
	 */
	public static ApnsEnvironment getSandboxEnvironment() {
		return new ApnsEnvironment("gateway.sandbox.push.apple.com", 2195, "feedback.sandbox.push.apple.com", 2196);
	}
}
