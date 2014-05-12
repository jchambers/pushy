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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * <p>A bounded-length buffer meant to store sent APNs notifications. This is necessary because the APNs protocol is
 * asynchronous, and notifications may be identified as failed or in need of retransmission after they've been
 * successfully written to the wire.</p>
 * 
 * <p>If a notification is present in the buffer, it is assumed to have been written to the outbound network buffer,
 * but its state is otherwise unknown.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
class MemorySentNotificationBuffer<E extends ApnsPushNotification> implements SentNotificationBuffer<E> {

	private final int capacity;
	private final ArrayDeque<SendableApnsPushNotification<E>> sentNotifications;

	/**
	 * Constructs a new sent notification buffer with the given maximum capacity.
	 * 
	 * @param capacity the capacity of the buffer
	 */
	public MemorySentNotificationBuffer(final int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("Capacity must be positive.");
		}

		this.capacity = capacity;
		this.sentNotifications = new ArrayDeque<SendableApnsPushNotification<E>>();
	}

	@Override
	public synchronized void addSentNotification(final SendableApnsPushNotification<E> notification) {
		this.sentNotifications.addLast(notification);

		while (this.sentNotifications.size() > this.capacity) {
			this.sentNotifications.removeFirst();
		}
	}

	@Override
	public synchronized void clearNotificationsBeforeSequenceNumber(final int sequenceNumber) {
		// We avoid a direct "greater than" comparison here to account for integer wrapping
		while (!this.sentNotifications.isEmpty() && sequenceNumber - this.sentNotifications.getFirst().getSequenceNumber() > 0) {
			this.sentNotifications.removeFirst();
		}
	}

	@Override
	public synchronized E getNotificationWithSequenceNumber(final int sequenceNumber) {
		for (final SendableApnsPushNotification<E> sentNotification : this.sentNotifications) {
			if (sentNotification.getSequenceNumber() == sequenceNumber) {
				return sentNotification.getPushNotification();
			}
		}

		return null;
	}

	@Override
	public synchronized List<E> getAllNotificationsAfterSequenceNumber(final int sequenceNumber) {
		final ArrayList<E> notifications = new ArrayList<E>(this.sentNotifications.size());

		for (final SendableApnsPushNotification<E> sentNotification : this.sentNotifications) {
			// We avoid a direct "greater than" comparison here to account for integer wrapping
			if (sentNotification.getSequenceNumber() - sequenceNumber > 0) {
				notifications.add(sentNotification.getPushNotification());
			}
		}

		notifications.trimToSize();
		return notifications;
	}

	@Override
	public void clearAllNotifications() {
		this.sentNotifications.clear();
	}

	@Override
	public int size() {
		return this.sentNotifications.size();
	}

	@Override
	public Integer getLowestSequenceNumber() {
		try {
			return this.sentNotifications.getFirst().getSequenceNumber();
		} catch (NoSuchElementException e) {
			return null;
		}
	}


	@Override
	public Integer getHighestSequenceNumber() {
		try {
			return this.sentNotifications.getLast().getSequenceNumber();
		} catch (NoSuchElementException e) {
			return null;
		}
	}
}