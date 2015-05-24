package com.relayrides.pushy.apns.util;

import java.util.Date;

/**
 * A utility class for working with deadlines and timeouts.
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
public class DeadlineUtil {

	/**
	 * Returns the number of milliseconds between the current time and the given deadline. If the given deadline is
	 * {@code null}, this method will return {@link java.lang.Long#MAX_VALUE}. Under no circumstances will this method
	 * return a result less than 1 millisecond (since many methods interpret a timeout of zero as a signal to wait
	 * indefinitely).
	 *
	 * @param deadline the deadline for which to calculate the number of milliseconds to wait
	 *
	 * @return the number of milliseconds to wait for the given deadline
	 */
	public static long getMillisToWaitForDeadline(final Date deadline) {
		final long millisToWaitForDeadline;

		if (deadline != null) {
			millisToWaitForDeadline = Math.max(deadline.getTime() - System.currentTimeMillis(), 1);
		} else {
			millisToWaitForDeadline = Long.MAX_VALUE;
		}

		return millisToWaitForDeadline;
	}

	/**
	 * Indicates whether the given deadline has passed. If the given deadline is {@code null}, this method will always
	 * return {@code false}.
	 *
	 * @param deadline the deadline to check
	 *
	 * @return {@code true} if the given deadline is in the past or {@code false} if the deadline is {@code null} or not
	 * in the past
	 */
	public static boolean hasDeadlineExpired(final Date deadline) {
		if (deadline != null) {
			return System.currentTimeMillis() > deadline.getTime();
		} else {
			return false;
		}
	}

}
