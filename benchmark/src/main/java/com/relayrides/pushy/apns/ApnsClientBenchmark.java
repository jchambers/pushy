package com.relayrides.pushy.apns;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.RandomStringUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

@State(Scope.Thread)
public class ApnsClientBenchmark {

    private EventLoopGroup eventLoopGroup;

    private ApnsClient<SimpleApnsPushNotification> client;
    private MockApnsServer server;

    private List<SimpleApnsPushNotification> pushNotifications;

    @Param({"10000"})
    public int notificationCount;

    @Param({"true", "false"})
    public boolean flushImmediately;

    private static final String CA_CERTIFICATE_FILENAME = "/ca.pem";
    private static final String SERVER_CERTIFICATES_FILENAME = "/server_certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server_key.pem";

    private static final String TEAM = "BENCHMARK";
    private static final String KEY_ID = "benchmark-key";
    private static final String TOPIC = "com.relayrides.pushy";
    private static final int TOKEN_LENGTH = 32;
    private static final int MESSAGE_BODY_LENGTH = 1024;

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    @Setup
    public void setUp() throws Exception {

        final KeyPair keyPair;
        {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

            keyPairGenerator.initialize(256, random);

            keyPair = keyPairGenerator.generateKeyPair();
        }

        this.eventLoopGroup = new NioEventLoopGroup(2);

        final ApnsClientBuilder<SimpleApnsPushNotification> clientBuilder = new ApnsClientBuilder<SimpleApnsPushNotification>()
                .setTrustedServerCertificateChain(ApnsClientBenchmark.class.getResourceAsStream(CA_CERTIFICATE_FILENAME))
                .setEventLoopGroup(this.eventLoopGroup);

        if (this.flushImmediately) {
            clientBuilder.setFlushThresholds(0, 0, TimeUnit.SECONDS);
        }

        this.client = clientBuilder.build();

        this.client.registerSigningKey(TEAM, KEY_ID, keyPair.getPrivate());
        this.client.registerTopicsForTeamId(TEAM, TOPIC);

        this.server = new MockApnsServerBuilder()
                .setServerCredentials(ApnsClientBenchmark.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME), ApnsClientBenchmark.class.getResourceAsStream(SERVER_KEY_FILENAME), null)
                .setTrustedClientCertificateChain(ApnsClientBenchmark.class.getResourceAsStream(CA_CERTIFICATE_FILENAME))
                .setEventLoopGroup(this.eventLoopGroup)
                .build();

        this.server.registerPublicKey(TEAM, KEY_ID, keyPair.getPublic());
        this.server.registerTopicsForTeamId(TEAM, TOPIC);

        final String token = generateRandomToken();
        this.server.registerDeviceTokenForTopic(TOPIC, token, null);

        this.pushNotifications = new ArrayList<>(this.notificationCount);

        final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();

        for (int i = 0; i < this.notificationCount; i++) {
            final String payload = payloadBuilder.setAlertBody(RandomStringUtils.randomAlphanumeric(MESSAGE_BODY_LENGTH))
                    .buildWithDefaultMaximumLength();

            this.pushNotifications.add(new SimpleApnsPushNotification(token, TOPIC, payload));
        }

        this.server.start(PORT).await();
        this.client.connect(HOST, PORT).await();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Threads(1)
    @Measurement(iterations = 10, batchSize = 1)
    @Warmup(iterations = 10, batchSize = 1)
    public long testSendNotifications() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(this.pushNotifications.size());

        for (final SimpleApnsPushNotification notification : this.pushNotifications) {
            this.client.sendNotification(notification).addListener(new GenericFutureListener<Future<PushNotificationResponse<SimpleApnsPushNotification>>>() {

                @Override
                public void operationComplete(final Future<PushNotificationResponse<SimpleApnsPushNotification>> future) {
                    if (future.isSuccess()) {
                        countDownLatch.countDown();
                    }
                }
            });
        }

        countDownLatch.await();
        return countDownLatch.getCount();
    }

    @TearDown
    public void tearDown() throws Exception {
        this.client.disconnect().await();
        this.server.shutdown().await();

        this.eventLoopGroup.shutdownGracefully().await();
    }

    private static String generateRandomToken() {
        final byte[] tokenBytes = new byte[TOKEN_LENGTH];
        new Random().nextBytes(tokenBytes);

        final StringBuilder builder = new StringBuilder(TOKEN_LENGTH * 2);

        for (final byte b : tokenBytes) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }
}
