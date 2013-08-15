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
class SentNotificationBuffer<E extends ApnsPushNotification> {
	
	private final ApnsPushNotification[] buffer;
	private int lastSequenceNumber;
	
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
		this.buffer[this.getArrayIndexForSequenceNumber(notification.getSequenceNumber())] =
				notification.getPushNotification();
		
		this.lastSequenceNumber = notification.getSequenceNumber();
	}
	
	/**
	 * Retrieves a notification from the buffer by its sequence number and removes that notification from the buffer.
	 * 
	 * @param sequenceNumber the sequence number of the notification to retrieve
	 * 
	 * @return the notification with the given sequence number or {@code null} if no such notification was found
	 */
	public synchronized E getAndRemoveNotificationWithSequenceNumber(final int sequenceNumber) {
		if (this.mayContainSequenceNumber(sequenceNumber)) {
			final int arrayIndex = this.getArrayIndexForSequenceNumber(sequenceNumber);
			
			@SuppressWarnings("unchecked")
			final E notification = (E) this.buffer[arrayIndex];
			this.buffer[arrayIndex] = null;
			
			return notification;
			
		} else {
			return null;
		}
	}
	
	/**
	 * Retrieves a list of all notifications received after the given notification sequence number (non-inclusive). All
	 * returned notifications are removed from the buffer.
	 * 
	 * @param sequenceNumber the sequence number of the notification (exclusive) after which to retrieve notifications
	 * 
	 * @return all notifications in the buffer sent after the given sequence number
	 */
	public synchronized List<E> getAndRemoveAllNotificationsAfterSequenceNumber(final int sequenceNumber) {
		final ArrayList<E> notifications = new ArrayList<E>();
		
		// Work around integer wrapping
		for (int id = sequenceNumber + 1; id - this.lastSequenceNumber <= 0; id++) {
			final E notification = this.getAndRemoveNotificationWithSequenceNumber(id);
			
			if (notification != null) {
				notifications.add(notification);
			}
		}
		
		return notifications;
	}
	
	private boolean mayContainSequenceNumber(final int sequenceNumber) {
		final int firstNotificationId = this.lastSequenceNumber - this.buffer.length;
		
		return sequenceNumber - firstNotificationId > 0;
	}
	
	private int getArrayIndexForSequenceNumber(final int sequenceNumber) {
		if (sequenceNumber >= 0) {
			return sequenceNumber % this.buffer.length;
		} else {
			return (this.buffer.length + (sequenceNumber % this.buffer.length)) % this.buffer.length;
		}
	}
}