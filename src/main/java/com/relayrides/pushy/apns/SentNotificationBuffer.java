package com.relayrides.pushy.apns;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>A fixed-length buffer meant to store sent APNs notifications. This is necessary because the APNs protocol is
 * asynchronous, and notifications may be identified as failed or in need of retransmission after they've been
 * successfully written to the wire.</p>
 * 
 * <p>If a notification is present in the buffer, its state is assumed to be unknown. Notifications are removed either
 * when they are known to have failed or be in need of retransmission or when enough subsequent messages are added to
 * push the notification out of the buffer.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 *
 * @param <E>
 */
public class SentNotificationBuffer<E extends ApnsPushNotification> {
	
	private final ApnsPushNotification[] buffer;
	private int lastNotificationId;
	
	/**
	 * Constructs a new sent notification buffer. Note that {@code capacity} MUST be a power of 2.
	 * 
	 * @param capacity the capacity of the buffer; must be a power of 2
	 */
	public SentNotificationBuffer(final int capacity) {
		if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
			throw new IllegalArgumentException("Capacity must be a positive power of two.");
		}
		
		this.buffer = new ApnsPushNotification[capacity];
	}
	
	/**
	 * Adds a sent notification to the buffer, potentially overwriting a previously-existing sent notification.
	 * 
	 * @param notification the notification to add to the buffer
	 */
	public synchronized void addSentNotification(SendableApnsPushNotification<E> notification) {
		this.buffer[this.getArrayIndexForNotificationId(notification.getNotificationId())] =
				notification.getPushNotification();
		
		this.lastNotificationId = notification.getNotificationId();
	}
	
	/**
	 * Retrieves a notification from the buffer by its ID and removes that notification from the buffer.
	 * 
	 * @param notificationId the ID of the notification to retrieve
	 * 
	 * @return the notification with the given ID or {@code null} if no such notification was found
	 */
	public synchronized E getAndRemoveNotificationWithId(final int notificationId) {
		if (this.mayContainNotificationId(notificationId)) {
			final int arrayIndex = this.getArrayIndexForNotificationId(notificationId);
			
			@SuppressWarnings("unchecked")
			final E notification = (E) this.buffer[arrayIndex];
			this.buffer[arrayIndex] = null;
			
			return notification;
			
		} else {
			return null;
		}
	}
	
	/**
	 * Retrieves a list of all notifications received after the given notification ID (non-inclusive). All
	 * returned notifications are removed from the buffer.
	 * 
	 * @param notificationId the ID of the notification (exclusive) after which to retrieve notifications
	 * 
	 * @return all notifications in the buffer sent after the given notification ID
	 */
	public synchronized List<E> getAndRemoveAllNotificationsAfterId(final int notificationId) {
		final ArrayList<E> notifications = new ArrayList<E>();
		
		// Work around integer wrapping
		for (int id = notificationId + 1; id - this.lastNotificationId <= 0; id++) {
			final E notification = this.getAndRemoveNotificationWithId(id);
			
			if (notification != null) {
				notifications.add(notification);
			}
		}
		
		return notifications;
	}
	
	private boolean mayContainNotificationId(final int notificationId) {
		final int firstNotificationId = this.lastNotificationId - this.buffer.length;
		
		return notificationId - firstNotificationId > 0;
	}
	
	private int getArrayIndexForNotificationId(final int notificationId) {
		if (notificationId >= 0) {
			return notificationId % this.buffer.length;
		} else {
			return (this.buffer.length + (notificationId % this.buffer.length)) % this.buffer.length;
		}
	}
}