package com.relayrides.pushy;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class SentNotificationBuffer<E extends ApnsPushNotification> {
	
	private final LinkedList<SendableApnsPushNotification<E>> buffer;
	private final int capacity;
	
	public SentNotificationBuffer(final int capacity) {
		this.buffer = new LinkedList<SendableApnsPushNotification<E>>();
		this.capacity = capacity;
	}
	
	public synchronized void addSentNotification(SendableApnsPushNotification<E> notification) {
		this.buffer.addLast(notification);
		
		while (buffer.size() > this.capacity) {
			this.buffer.removeFirst();
		}
	}
	
	public synchronized E getFailedNotificationAndClearPriorNotifications(final int failedNotificationId) {
		while (this.buffer.getFirst().isSequentiallyBefore(failedNotificationId)) {
			this.buffer.removeFirst();
		}
		
		return this.buffer.getFirst().getNotificationId() == failedNotificationId ?
				this.buffer.removeFirst().getPushNotification() : null;
	}
	
	public synchronized List<E> getAllNotifications() {
		final ArrayList<E> notifications = new ArrayList<E>(this.buffer.size());
		
		for (SendableApnsPushNotification<E> sentNotification : this.buffer) {
			notifications.add(sentNotification.getPushNotification());
		}
		
		return notifications;
	}
	
	public synchronized void clear() {
		this.buffer.clear();
	}
}
