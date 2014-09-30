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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Date;

import org.junit.Test;


public class FeedbackServiceConnectionTest extends BasePushyTest {

	private class TestListener implements FeedbackServiceListener {

		private final Object mutex;

		private boolean connectionSucceeded = false;
		private boolean connectionFailed = false;
		private boolean connectionClosed = false;

		private Throwable connectionFailureCause;

		private final ArrayList<ExpiredToken> expiredTokens = new ArrayList<ExpiredToken>();

		public TestListener(final Object mutex) {
			this.mutex = mutex;
		}

		@Override
		public void handleConnectionSuccess(final FeedbackServiceConnection connection) {
			synchronized (this.mutex) {
				this.connectionSucceeded = true;
				this.mutex.notifyAll();
			}
		}

		@Override
		public void handleConnectionFailure(final FeedbackServiceConnection connection, final Throwable cause) {
			synchronized (this.mutex) {
				this.connectionFailed = true;
				this.connectionFailureCause = cause;

				this.mutex.notifyAll();
			}
		}

		@Override
		public void handleExpiredToken(final FeedbackServiceConnection connection, final ExpiredToken token) {
			this.expiredTokens.add(token);
		}

		@Override
		public void handleConnectionClosure(final FeedbackServiceConnection connection) {
			synchronized (this.mutex) {
				this.connectionClosed = true;
				this.mutex.notifyAll();
			}
		}
	}

	private static final String TEST_CONNECTION_NAME = "TestFeedbackConnection";

	@Test
	public void testGetExpiredTokens() throws Exception {
		final Object mutex = new Object();

		final TestListener listener = new TestListener(mutex);

		final FeedbackServiceConnection feedbackConnection =
				new FeedbackServiceConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
						this.getEventLoopGroup(), new FeedbackConnectionConfiguration(), listener, TEST_CONNECTION_NAME);

		assertTrue(listener.expiredTokens.isEmpty());

		// Dates will have some loss of precision since APNS only deals with SECONDS since the epoch; we choose
		// timestamps that just happen to be on full seconds.
		final ExpiredToken firstToken = new ExpiredToken(new byte[] { 97, 44, 32, 16, 16 }, new Date(1375760188000L));
		final ExpiredToken secondToken = new ExpiredToken(new byte[] { 77, 62, 40, 30, 8 }, new Date(1375760188000L));

		this.getFeedbackServer().addExpiredToken(firstToken);
		this.getFeedbackServer().addExpiredToken(secondToken);

		synchronized (mutex) {
			feedbackConnection.connect();

			while (!listener.connectionSucceeded) {
				mutex.wait();
			}
		}

		assertTrue(listener.connectionSucceeded);

		synchronized (mutex) {
			while (!listener.connectionClosed) {
				mutex.wait();
			}
		}

		assertEquals(2, listener.expiredTokens.size());
		assertTrue(listener.expiredTokens.contains(firstToken));
		assertTrue(listener.expiredTokens.contains(secondToken));
	}

	@Test
	public void testGetExpiredTokensCloseWhenDone() throws Exception {
		this.getFeedbackServer().setCloseWhenDone(true);
		this.testGetExpiredTokens();
	}
}
