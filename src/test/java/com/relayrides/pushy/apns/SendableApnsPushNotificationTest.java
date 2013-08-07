package com.relayrides.pushy.apns;

import static org.junit.Assert.*;

import java.util.Date;

import org.junit.Test;

import com.relayrides.pushy.apns.ApnsPushNotification;
import com.relayrides.pushy.apns.SendableApnsPushNotification;

public class SendableApnsPushNotificationTest {

	@Test
	public void testIsSequentiallyAfter() {
		final ApnsPushNotification testNotification = new ApnsPushNotification() {

			public byte[] getToken() {
				return new byte[32];
			}

			public String getPayload() {
				return "This is only a test.";
			}

			public Date getDeliveryInvalidationTime() {
				return null;
			}
		};
		
		{
			final SendableApnsPushNotification<ApnsPushNotification> firstSendable =
					new SendableApnsPushNotification<ApnsPushNotification>(testNotification, 0);
			
			final SendableApnsPushNotification<ApnsPushNotification> secondSendable =
					new SendableApnsPushNotification<ApnsPushNotification>(testNotification, 1);
			
			assertTrue(firstSendable.isSequentiallyBefore(secondSendable.getNotificationId()));
			assertFalse(secondSendable.isSequentiallyBefore(firstSendable.getNotificationId()));
			assertFalse(firstSendable.isSequentiallyBefore(firstSendable.getNotificationId()));
		}
		
		{
			final SendableApnsPushNotification<ApnsPushNotification> firstSendable =
					new SendableApnsPushNotification<ApnsPushNotification>(testNotification, Integer.MAX_VALUE);
			
			final SendableApnsPushNotification<ApnsPushNotification> secondSendable =
					new SendableApnsPushNotification<ApnsPushNotification>(testNotification, Integer.MAX_VALUE + 1);
			
			assertTrue(firstSendable.isSequentiallyBefore(secondSendable.getNotificationId()));
			assertFalse(secondSendable.isSequentiallyBefore(firstSendable.getNotificationId()));
			assertFalse(firstSendable.isSequentiallyBefore(firstSendable.getNotificationId()));
		}
		
		{
			final SendableApnsPushNotification<ApnsPushNotification> firstSendable =
					new SendableApnsPushNotification<ApnsPushNotification>(testNotification, Integer.MAX_VALUE + 1);
			
			final SendableApnsPushNotification<ApnsPushNotification> secondSendable =
					new SendableApnsPushNotification<ApnsPushNotification>(testNotification, Integer.MAX_VALUE + 2);
			
			assertTrue(firstSendable.isSequentiallyBefore(secondSendable.getNotificationId()));
			assertFalse(secondSendable.isSequentiallyBefore(firstSendable.getNotificationId()));
			assertFalse(firstSendable.isSequentiallyBefore(firstSendable.getNotificationId()));
		}
	}

}
