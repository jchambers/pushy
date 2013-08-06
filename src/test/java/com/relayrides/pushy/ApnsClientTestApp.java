package com.relayrides.pushy;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Date;

import com.relayrides.pushy.ApnsEnvironment;
import com.relayrides.pushy.ApnsPushNotification;
import com.relayrides.pushy.FailedDeliveryListener;
import com.relayrides.pushy.PushManager;

public class ApnsClientTestApp {

	public static void main(final String[] args) throws Exception {
		final String certPath = "/Users/jon/Desktop/apns-dev.p12";
		final String password = "dJSMzu57vvB6Zsq";

		final KeyStore keyStore = KeyStore.getInstance("PKCS12");
		keyStore.load(new FileInputStream(certPath), password.toCharArray());
		
		final PushManager pushManager = new PushManager(ApnsEnvironment.getSandboxEnvironment(), keyStore, password.toCharArray());
		
		final FailedDeliveryListener listener = new FailedDeliveryListener() {

			public void handleFailedDelivery(ApnsPushNotification notification, Throwable cause) {
				if (cause instanceof ApnsException) {
					final ApnsException e = (ApnsException)cause;
					System.err.println(String.format("Delivery failed with error code %s.", e.getErrorCode()));
				}
			}
			
		};
		
		pushManager.registerFailedDeliveryListener(listener);
		
		pushManager.start();
		
		pushManager.enqueuePushNotification(new ApnsPushNotification() {

			public byte[] getToken() {
				return new byte[32];
			}

			public String getPayload() {
				return "HEY YOU GUYS !!";
			}

			public Date getExpiration() {
				return new Date();
			}});
		
		pushManager.enqueuePushNotification(new ApnsPushNotification() {

			public byte[] getToken() {
				return new byte[32];
			}

			public String getPayload() {
				return "This is only a test.";
			}

			public Date getExpiration() {
				return new Date();
			}});
		
		Thread.sleep(10000);
		
		pushManager.shutdown();
	}

}
