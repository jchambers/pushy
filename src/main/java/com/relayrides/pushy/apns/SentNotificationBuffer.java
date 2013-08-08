package com.relayrides.pushy.apns;

import java.util.ArrayList;
import java.util.List;

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
	
	public synchronized void addSentNotification(SendableApnsPushNotification<E> notification) {
		this.buffer[this.getArrayIndexForNotificationId(notification.getNotificationId())] =
				notification.getPushNotification();
		
		this.lastNotificationId = notification.getNotificationId();
	}
	
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
	
	public synchronized List<E> getAndRemoveAllNotificationsAfterId(final int notificationId) {
		final ArrayList<E> notifications = new ArrayList<E>();
		
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