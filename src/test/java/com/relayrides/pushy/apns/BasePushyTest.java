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

import static org.junit.Assert.fail;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public abstract class BasePushyTest {
	public static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", 2195, "127.0.0.1", 2196);

	private static final long LATCH_TIMEOUT_VALUE = 2;
	private static final TimeUnit LATCH_TIMEOUT_UNIT = TimeUnit.SECONDS;

	private static NioEventLoopGroup eventLoopGroup;

	private PushManager<SimpleApnsPushNotification> pushManager;
	private MockApnsServer apnsServer;
	private MockFeedbackServer feedbackServer;

	@Rule
	public Timeout globalTimeout = new Timeout(10000);

	@BeforeClass
	public static void setUpBeforeClass() {
		BasePushyTest.eventLoopGroup = new NioEventLoopGroup();
	}

	@Before
	public void setUp() throws Exception {

		this.apnsServer = new MockApnsServer(TEST_ENVIRONMENT.getApnsGatewayPort(), BasePushyTest.eventLoopGroup);
		this.apnsServer.start();

		this.feedbackServer = new MockFeedbackServer(TEST_ENVIRONMENT.getFeedbackPort(), BasePushyTest.eventLoopGroup);
		this.feedbackServer.start();

		this.pushManager = new PushManager<SimpleApnsPushNotification>(TEST_ENVIRONMENT,
				SSLTestUtil.createSSLContextForTestClient(), BasePushyTest.eventLoopGroup, null, null,
				new PushManagerConfiguration(), "Test push manager");
	}

	@After
	public void tearDown() throws InterruptedException {
		this.apnsServer.shutdown();
		this.feedbackServer.shutdown();
	}

	@AfterClass
	public static void tearDownAfterClass() throws InterruptedException {
		BasePushyTest.eventLoopGroup.shutdownGracefully().await();
	}

	public NioEventLoopGroup getEventLoopGroup() {
		return BasePushyTest.eventLoopGroup;
	}

	public PushManager<SimpleApnsPushNotification> getPushManager() {
		return this.pushManager;
	}

	public MockApnsServer getApnsServer() {
		return this.apnsServer;
	}

	public MockFeedbackServer getFeedbackServer() {
		return this.feedbackServer;
	}

	public SimpleApnsPushNotification createTestNotification() {
		final byte[] token = new byte[MockApnsServer.EXPECTED_TOKEN_SIZE];
		new Random().nextBytes(token);

		return new SimpleApnsPushNotification(token, "{\"aps\":{\"alert\":\"Hello\"}}");
	}

	public void waitForLatch(final CountDownLatch latch) throws InterruptedException {
		while (latch.getCount() > 0) {
			if (!latch.await(LATCH_TIMEOUT_VALUE, LATCH_TIMEOUT_UNIT)) {
				fail(String.format("Timed out waiting for latch. Remaining count: %d", latch.getCount()));
			}
		}
	}
}
