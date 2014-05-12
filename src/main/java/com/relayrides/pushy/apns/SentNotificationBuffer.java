package com.relayrides.pushy.apns;

import java.util.List;

/**
 * Interface extracted from the MemorySentNotificationBuffer (previous 'SentNotificationBuffer'), to allow easy replacement of the buffer implementation.
 * 
 * @author <a href="mailto:flozano@gmail.com">Francisco A. Lozano</a>
 *
 * @param <E>
 */
public interface SentNotificationBuffer<E extends ApnsPushNotification> {

	/**
	 * Adds a sent notification to the buffer, potentially discarding a previously-existing sent notification.
	 * 
	 * @param notification the notification to add to the buffer
	 */
	void addSentNotification(SendableApnsPushNotification<E> notification);

	/**
	 * Removes all sent notifications from the buffer if they come before the given sequence number (exclusive and
	 * accounting for potential integer wrapping).
	 * 
	 * @param sequenceNumber the sequence number (exclusive) before which to remove sent notifications
	 */
	void clearNotificationsBeforeSequenceNumber(int sequenceNumber);

	/**
	 * Retrieves a notification from the buffer by its sequence number.
	 * 
	 * @param sequenceNumber the sequence number of the notification to retrieve
	 * 
	 * @return the notification with the given sequence number or {@code null} if no such notification was found
	 */
	E getNotificationWithSequenceNumber(int sequenceNumber);

	/**
	 * Retrieves a list of all notifications received after the given notification sequence number (non-inclusive).
	 * 
	 * @param sequenceNumber the sequence number of the notification (exclusive) after which to retrieve notifications
	 * 
	 * @return all notifications in the buffer sent after the given sequence number
	 */
	List<E> getAllNotificationsAfterSequenceNumber(int sequenceNumber);

	/**
	 * Removes all notifications from the buffer.
	 */
	void clearAllNotifications();

	/**
	 * Returns the sequence number of the newest item in this buffer.
	 * 
	 * @return the sequence number of the newest item in this buffer or {@code null} if this buffer is empty
	 */
	Integer getHighestSequenceNumber();

	/**
	 * Returns the sequence number of the oldest item in this buffer.
	 * 
	 * @return the sequence number of the oldest item in this buffer or {@code null} if this buffer is empty
	 */
	Integer getLowestSequenceNumber();

	/**
	 * Returns the number of notifications currently stored in this buffer.
	 * 
	 * @return the number of notifications currently stored in this buffer
	 */
	int size();

}