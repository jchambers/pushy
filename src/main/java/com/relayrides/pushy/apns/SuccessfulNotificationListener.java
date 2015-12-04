package com.relayrides.pushy.apns;

/**
 * <p>Listens for a successful push notification. Listeners will be informed when the APNs server succeed
 * a notification you sent.</p>
 *
 * @author <a href="mailto:devhak2@gmail.com">Younghak Lee</a>
 * 
 * @see com.relayrides.pushy.apns.PushManager#registerSuccessfulNotificationListener(SuccessfulNotificationListener)
 * @see com.relayrides.pushy.apns.PushManager#unregisterSuccessfulNotificationListener(SuccessfulNotificationListener)
 */
public interface SuccessfulNotificationListener<T extends ApnsPushNotification> {
	/**
	 * Handles a successful push notification.
	 * 
	 * @param pushManager
	 * @param notification
	 */
	void handleSuccessfulNotification(PushManager<? extends T> pushManager, T notification);
}
