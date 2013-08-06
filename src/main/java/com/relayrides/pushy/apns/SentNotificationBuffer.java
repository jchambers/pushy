package com.relayrides.pushy.apns;

import java.util.ArrayList;
import java.util.LinkedList;

public class SentNotificationBuffer<E extends ApnsPushNotification> {
	
	private final LinkedList<SendableApnsPushNotification<E>> buffer;
	private final int capacity;
	
	public SentNotificationBuffer(final int capacity) {
		this.buffer = new LinkedList<SendableApnsPushNotification<E>>();
		this.capacity = capacity;
	}
	
	public synchronized void addSentNotification(SendableApnsPushNotification<E> notification) {
		this.buffer.addLast(notification);
		
		while (this.buffer.size() > this.capacity) {
			this.buffer.removeFirst();
		}
	}
	
	public synchronized E getFailedNotificationAndClearBuffer(final int failedNotificationId, final PushManager<E> pushManager) {
		while (this.buffer.getFirst().isSequentiallyBefore(failedNotificationId)) {
			this.buffer.removeFirst();
		}
		
		final E failedNotification = this.buffer.getFirst().getNotificationId() == failedNotificationId ?
				this.buffer.removeFirst().getPushNotification() : null;
		
		{
			final ArrayList<E> unsentNotifications = new ArrayList<E>(this.buffer.size());
			
			for (final SendableApnsPushNotification<E> sentNotification : this.buffer) {
				unsentNotifications.add(sentNotification.getPushNotification());
			}
			
			pushManager.enqueueAllNotifications(unsentNotifications);
		}
		
		this.buffer.clear();
		
		return failedNotification;
	}
}
