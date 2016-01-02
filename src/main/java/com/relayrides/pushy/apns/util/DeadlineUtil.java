/* Copyright (c) 2015 RelayRides
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

package com.relayrides.pushy.apns.util;

import java.util.Date;

/**
 * Contains utility methods for working with deadlines and timeouts.
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
