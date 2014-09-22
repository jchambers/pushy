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

import java.util.Date;
import java.util.List;

import org.junit.Test;

public class FeedbackServiceConnectionTest extends BasePushyTest {

	@Test
	public void testGetExpiredTokens() throws Exception {
		final FeedbackServiceConnection feedbackClient =
				new FeedbackServiceConnection(TEST_ENVIRONMENT, SSLTestUtil.createSSLContextForTestClient(),
						this.getEventLoopGroup(), new FeedbackConnectionConfiguration());

		assertTrue(feedbackClient.getExpiredTokens().isEmpty());

		// Dates will have some loss of precision since APNS only deals with SECONDS since the epoch; we choose
		// timestamps that just happen to be on full seconds.
		final ExpiredToken firstToken = new ExpiredToken(new byte[] { 97, 44, 32, 16, 16 }, new Date(1375760188000L));
		final ExpiredToken secondToken = new ExpiredToken(new byte[] { 77, 62, 40, 30, 8 }, new Date(1375760188000L));

		this.getFeedbackServer().addExpiredToken(firstToken);
		this.getFeedbackServer().addExpiredToken(secondToken);

		final List<ExpiredToken> expiredTokens = feedbackClient.getExpiredTokens();

		assertEquals(2, expiredTokens.size());
		assertTrue(expiredTokens.contains(firstToken));
		assertTrue(expiredTokens.contains(secondToken));

		assertTrue(feedbackClient.getExpiredTokens().isEmpty());
	}

	@Test
	public void testGetExpiredTokensCloseWhenDone() throws Exception {
		this.getFeedbackServer().setCloseWhenDone(true);
		this.testGetExpiredTokens();
	}
}
