/* Copyright (c) 2014 RelayRides
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

import io.netty.channel.nio.NioEventLoopGroup;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class BenchmarkApp {

	private static final int NOTIFICATIONS_PER_TEST = 100000;

	private static final int GATEWAY_PORT = 2195;
	private static final int FEEDPACK_PORT = 2196;

	private static final ApnsEnvironment BENCHMARK_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", GATEWAY_PORT, "localhost", FEEDPACK_PORT);

	private NioEventLoopGroup serverEventLoopGroup;
	private MockApnsServer server;

	private final ArrayList<SimpleApnsPushNotification> notifications =
			new ArrayList<SimpleApnsPushNotification>(NOTIFICATIONS_PER_TEST);

	private class BenchmarkErrorListener implements RejectedNotificationListener<SimpleApnsPushNotification>, FailedConnectionListener<SimpleApnsPushNotification> {

		@Override
		public void handleFailedConnection(final PushManager<? extends SimpleApnsPushNotification> pushManager, final Throwable cause) {
			System.err.println("Connection failed.");
			cause.printStackTrace(System.err);
		}

		@Override
		public void handleRejectedNotification(final PushManager<? extends SimpleApnsPushNotification> pushManager, final SimpleApnsPushNotification notification, final RejectedNotificationReason rejectionReason) {
			System.err.format("%s rejected: %s\n", notification, rejectionReason);
		}
	}

	public BenchmarkApp() throws Exception {
		final ApnsPayloadBuilder builder = new ApnsPayloadBuilder();
		final Random random = new Random();

		for (int i = 0; i < NOTIFICATIONS_PER_TEST; i++) {
			final byte[] token = new byte[32];
			random.nextBytes(token);

			builder.setAlertBody(new BigInteger(1024, new Random()).toString(16));

			this.notifications.add(new SimpleApnsPushNotification(token, builder.buildWithDefaultMaximumLength()));
		}
	}

	public void runAllBenchmarks() throws InterruptedException {
		this.serverEventLoopGroup = new NioEventLoopGroup(2);
		this.server = new MockApnsServer(GATEWAY_PORT, serverEventLoopGroup);

		// We want to do a dummy run first to let the JVM warm up
		final NioEventLoopGroup warmupGroup = new NioEventLoopGroup(1);
		this.runBenchmark(warmupGroup, 1, this.notifications);
		warmupGroup.shutdownGracefully().await();

		System.out.println("threads, connections, throughput [k/sec]");

		for (int eventLoopGroupThreadCount : new int[] {1, 2, 4, 8, 16}) {
			final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(eventLoopGroupThreadCount);

			for (int concurrentConnectionCount : new int[] {1, 2, 4, 8, 16, 32}) {
				if (concurrentConnectionCount >= eventLoopGroupThreadCount) {
					System.gc();

					double throughput = this.runBenchmark(eventLoopGroup, concurrentConnectionCount, this.notifications);

					System.out.format("%d, %d, %.1f\n",
							eventLoopGroupThreadCount,
							concurrentConnectionCount,
							throughput / 1000.0);
				}
			}

			eventLoopGroup.shutdownGracefully().await();
		}

		this.serverEventLoopGroup.shutdownGracefully().await();
	}

	private double runBenchmark(final NioEventLoopGroup eventLoopGroup, final int concurrentConnectionCount, final List<SimpleApnsPushNotification> notifications) throws InterruptedException {
		final PushManagerConfiguration configuration = new PushManagerConfiguration();
		configuration.setConcurrentConnectionCount(concurrentConnectionCount);

		final PushManager<SimpleApnsPushNotification> pushManager;

		try {
			pushManager = new PushManager<SimpleApnsPushNotification>(BENCHMARK_ENVIRONMENT,
					SSLTestUtil.createSSLContextForTestClient(), eventLoopGroup, null, null, configuration,
					"Benchmark push manager");
		} catch (Exception e) {
			throw new RuntimeException("Failed to create push manager.", e);
		}

		final BenchmarkErrorListener errorListener = new BenchmarkErrorListener();

		pushManager.registerFailedConnectionListener(errorListener);
		pushManager.registerRejectedNotificationListener(errorListener);
		pushManager.getQueue().addAll(notifications);

		final CountDownLatch firstMessageLatch = this.server.getAcceptedNotificationCountDownLatch(1);
		final CountDownLatch lastMessageLatch = this.server.getAcceptedNotificationCountDownLatch(notifications.size());

		this.server.start();
		pushManager.start();

		if (!firstMessageLatch.await(10, TimeUnit.SECONDS)) {
			System.err.println("Timed out waiting for first message.");
		}

		long start = System.currentTimeMillis();

		if (!lastMessageLatch.await(10, TimeUnit.SECONDS)) {
			System.err.println("Timed out waiting for last message. Remaining count: " + lastMessageLatch.getCount());
		}

		long end = System.currentTimeMillis();

		pushManager.shutdown();
		this.server.shutdown();

		return 1000.0 * ((double)notifications.size() / (double)(end - start));
	}

	public static void main(String[] args) throws Exception {
		final BenchmarkApp benchmarkApp = new BenchmarkApp();
		benchmarkApp.runAllBenchmarks();
	}
}
