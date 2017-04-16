package com.relayrides.pushy.apns;

import com.relayrides.pushy.apns.auth.ApnsSigningKey;
import com.relayrides.pushy.apns.util.ApnsPayloadBuilder;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.lang3.RandomStringUtils;
import org.openjdk.jmh.annotations.*;

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

@State(Scope.Thread)
public class ApnsClientBenchmark {

    private NioEventLoopGroup eventLoopGroup;

    private ApnsClient client;
    private BenchmarkApnsServer server;

    private List<SimpleApnsPushNotification> pushNotifications;

    @Param({"10000"})
    public int notificationCount;

    private static final String CA_CERTIFICATE_FILENAME = "/ca.pem";
    private static final String SERVER_CERTIFICATES_FILENAME = "/server_certs.pem";
    private static final String SERVER_KEY_FILENAME = "/server_key.pem";

    private static final String TOPIC = "com.relayrides.pushy";
    private static final String TEAM_ID = "benchmark.team";
    private static final String KEY_ID = "benchmark.key";
    private static final int TOKEN_LENGTH = 32;
    private static final int MESSAGE_BODY_LENGTH = 1024;

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    @Setup
    public void setUp() throws Exception {
        this.eventLoopGroup = new NioEventLoopGroup(2);

        final ApnsSigningKey signingKey;
        {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            final SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

            keyPairGenerator.initialize(256, random);

            signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPairGenerator.generateKeyPair().getPrivate());
        }

        final ApnsClientBuilder clientBuilder = new ApnsClientBuilder()
                .setSigningKey(signingKey)
                .setTrustedServerCertificateChain(ApnsClientBenchmark.class.getResourceAsStream(CA_CERTIFICATE_FILENAME))
                .setEventLoopGroup(this.eventLoopGroup);

        this.client = clientBuilder.build();
        this.server = new BenchmarkApnsServer(ApnsClientBenchmark.class.getResourceAsStream(SERVER_CERTIFICATES_FILENAME),
                ApnsClientBenchmark.class.getResourceAsStream(SERVER_KEY_FILENAME),
                this.eventLoopGroup);

        final String token = generateRandomToken();

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
    @Measurement(iterations = 20, batchSize = 1)
    @Warmup(iterations = 20, batchSize = 1)
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
