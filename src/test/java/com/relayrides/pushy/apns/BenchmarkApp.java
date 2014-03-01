package com.relayrides.pushy.apns;

import io.netty.channel.nio.NioEventLoopGroup;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.relayrides.pushy.apns.ApnsEnvironment;
import com.relayrides.pushy.apns.PushManager;
import com.relayrides.pushy.apns.PushManagerFactory;
import com.relayrides.pushy.apns.RejectedNotificationListener;
import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class BenchmarkApp {

	private static final int NOTIFICATIONS_PER_TEST = 20000;

	private static final int GATEWAY_PORT = 2195;
	private static final int FEEDPACK_PORT = 2196;

	private final PushManagerFactory<SimpleApnsPushNotification> pushManagerFactory;

	private NioEventLoopGroup serverEventLoopGroup;
	private MockApnsServer server;

	private final ArrayList<SimpleApnsPushNotification> notifications =
			new ArrayList<SimpleApnsPushNotification>(NOTIFICATIONS_PER_TEST);

	private class BenchmarkErrorListener implements RejectedNotificationListener<SimpleApnsPushNotification>, FailedConnectionListener<SimpleApnsPushNotification> {

		public void handleFailedConnection(final PushManager<? extends SimpleApnsPushNotification> pushManager, final Throwable cause) {
			System.err.println("Connection failed.");
			cause.printStackTrace(System.err);
		}

		public void handleRejectedNotification(final PushManager<? extends SimpleApnsPushNotification> pushManager, final SimpleApnsPushNotification notification, final RejectedNotificationReason rejectionReason) {
			System.err.format("%s rejected: %s\n", notification, rejectionReason);
		}
	}

	public BenchmarkApp() throws Exception {
		this.pushManagerFactory = new PushManagerFactory<SimpleApnsPushNotification>(
				new ApnsEnvironment("127.0.0.1", GATEWAY_PORT, "localhost", FEEDPACK_PORT),
				SSLTestUtil.createSSLContextForTestClient());

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
		this.serverEventLoopGroup = new NioEventLoopGroup(4);
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
		this.pushManagerFactory.setEventLoopGroup(eventLoopGroup);
		this.pushManagerFactory.setConcurrentConnectionCount(concurrentConnectionCount);

		final PushManager<SimpleApnsPushNotification> pushManager = this.pushManagerFactory.buildPushManager();

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