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

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public abstract class BasePushyTest {
	public static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", 2195, "127.0.0.1", 2196);

	private static final long LATCH_TIMEOUT_VALUE = 2;
	private static final TimeUnit LATCH_TIMEOUT_UNIT = TimeUnit.SECONDS;

	private NioEventLoopGroup workerGroup;

	private PushManager<SimpleApnsPushNotification> pushManager;
	private MockApnsServer apnsServer;
	private MockFeedbackServer feedbackServer;

	@Before
	public void setUp() throws InterruptedException, UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {

		this.workerGroup = new NioEventLoopGroup();

		this.apnsServer = new MockApnsServer(TEST_ENVIRONMENT.getApnsGatewayPort(), this.workerGroup);
		this.apnsServer.start();

		this.feedbackServer = new MockFeedbackServer(TEST_ENVIRONMENT.getFeedbackPort(), this.workerGroup);
		this.feedbackServer.start();

		final PushManagerFactory<SimpleApnsPushNotification> pushManagerFactory =
				new PushManagerFactory<SimpleApnsPushNotification>(TEST_ENVIRONMENT, SSLUtil.createSSLContextForTestClient());

		pushManagerFactory.setEventLoopGroup(this.workerGroup);

		this.pushManager = pushManagerFactory.buildPushManager();
	}

	@After
	public void tearDown() throws InterruptedException {
		this.apnsServer.shutdown();
		this.feedbackServer.shutdown();

		this.workerGroup.shutdownGracefully().await();
	}

	public NioEventLoopGroup getWorkerGroup() {
		return this.workerGroup;
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
		return new SimpleApnsPushNotification(new byte[] { 0x12, 0x34, 0x56 }, "{\"aps\":{\"alert\":\"Hello\"}}");
	}

	public void waitForLatch(final CountDownLatch latch) throws InterruptedException {
		while (latch.getCount() > 0) {
			if (!latch.await(LATCH_TIMEOUT_VALUE, LATCH_TIMEOUT_UNIT)) {
				fail(String.format("Timed out waiting for latch. Remaining count: %d", latch.getCount()));
			}
		}
	}
}
