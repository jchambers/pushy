/*
 * Copyright (c) 2013-2017 Turo
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

package com.turo.pushy.apns;

import com.turo.pushy.apns.auth.ApnsSigningKey;
import com.turo.pushy.apns.util.ApnsPayloadBuilder;
import com.turo.pushy.apns.util.SimpleApnsPushNotification;
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

    @Param({"1", "4", "8"})
    public int concurrentConnections;

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
                .setApnsServer(HOST, PORT)
                .setConcurrentConnections(this.concurrentConnections)
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
        this.client.close().await();
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
