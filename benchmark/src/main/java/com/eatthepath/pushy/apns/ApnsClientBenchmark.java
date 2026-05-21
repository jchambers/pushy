/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns;

import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.server.BenchmarkApnsServer;
import com.eatthepath.pushy.apns.server.BenchmarkApnsServerBuilder;
import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.concurrent.Future;
import org.apache.commons.lang3.RandomStringUtils;
import org.openjdk.jmh.annotations.*;

import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

@State(Scope.Thread)
public class ApnsClientBenchmark {

    private ApnsClientResources clientResources;
    private NioEventLoopGroup serverEventLoopGroup;

    private ApnsClient client;
    private BenchmarkApnsServer server;

    private List<SimpleApnsPushNotification> pushNotifications;

    @Param({"10000"})
    public int notificationCount;

    @Param({"1", "4", "8"})
    public int concurrentConnections;

    protected static X509Bundle CA_BUNDLE;
    protected static X509Bundle SERVER_CERTIFICATE_BUNDLE;

    private static final String TOPIC = "com.eatthepath.pushy";
    private static final String TEAM_ID = "benchmark.team";
    private static final String KEY_ID = "benchmark.key";
    private static final int TOKEN_LENGTH = 32;
    private static final int MESSAGE_BODY_LENGTH = 1024;

    private static final String HOST = "localhost";
    private static final int PORT = 8443;

    private static final int KEY_SIZE = 256;

    @Setup
    public void setUp() throws Exception {
        this.clientResources = new ApnsClientResources(new NioEventLoopGroup(this.concurrentConnections));
        this.serverEventLoopGroup = new NioEventLoopGroup(this.concurrentConnections);

        final Instant now = Instant.now();

        final CertificateBuilder rootCertificateBuilderTemplate = new CertificateBuilder()
            .notBefore(now)
            .notAfter(now.plus(Duration.ofHours(8)));

        CA_BUNDLE = rootCertificateBuilderTemplate.copy()
            .subject("CN=PushyTestRoot")
            .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature, CertificateBuilder.KeyUsage.keyCertSign)
            .setIsCertificateAuthority(true)
            .buildSelfSigned();

        SERVER_CERTIFICATE_BUNDLE = rootCertificateBuilderTemplate.copy()
            .subject("CN=com.eatthepath.pushy")
            .setKeyUsage(true, CertificateBuilder.KeyUsage.digitalSignature, CertificateBuilder.KeyUsage.keyEncipherment)
            .addExtendedKeyUsage(CertificateBuilder.ExtendedKeyUsage.PKIX_KP_CLIENT_AUTH)
            .addExtendedKeyUsage(CertificateBuilder.ExtendedKeyUsage.PKIX_KP_SERVER_AUTH)
            .setIsCertificateAuthority(false)
            .addSanDnsName("localhost")
            .buildIssuedBy(CA_BUNDLE);

        final ApnsSigningKey signingKey;
        {
            final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(KEY_SIZE, new SecureRandom());

            signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) keyPairGenerator.generateKeyPair().getPrivate());
        }

        this.client = new ApnsClientBuilder()
                .setApnsServer(HOST, PORT)
                .setConcurrentConnections(this.concurrentConnections)
                .setSigningKey(signingKey)
                .setTrustedServerCertificateChain(CA_BUNDLE.getCertificate())
                .setApnsClientResources(this.clientResources)
                .build();

        this.server = new BenchmarkApnsServerBuilder()
                .setServerCredentials(SERVER_CERTIFICATE_BUNDLE.getCertificatePathWithRoot(), SERVER_CERTIFICATE_BUNDLE.getKeyPair().getPrivate())
                .setTrustedClientCertificateChain(CA_BUNDLE.getCertificate())
                .setEventLoopGroup(this.serverEventLoopGroup)
                .build();

        this.pushNotifications = new ArrayList<>(this.notificationCount);
        {
            final ApnsPayloadBuilder payloadBuilder = new ApnsPayloadBuilder();

            for (int i = 0; i < this.notificationCount; i++) {
                final String payload =
                        payloadBuilder.setAlertBody(RandomStringUtils.randomAlphanumeric(MESSAGE_BODY_LENGTH)).build();

                this.pushNotifications.add(new SimpleApnsPushNotification(generateRandomDeviceToken(), TOPIC, payload));
            }
        }

        this.server.start(PORT).get();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Threads(1)
    @Measurement(iterations = 20, batchSize = 1)
    @Warmup(iterations = 20, batchSize = 1)
    public long testSendNotifications() throws InterruptedException {
        final CountDownLatch countDownLatch = new CountDownLatch(this.pushNotifications.size());

        for (final SimpleApnsPushNotification notification : this.pushNotifications) {
            this.client.sendNotification(notification).thenRun(countDownLatch::countDown);
        }

        countDownLatch.await();
        return countDownLatch.getCount();
    }

    @TearDown
    public void tearDown() throws Exception {
        this.client.close().get();
        this.server.shutdown().get();

        final Future<?> clientShutdownFuture = this.clientResources.shutdownGracefully();
        final Future<?> serverShutdownFuture = this.serverEventLoopGroup.shutdownGracefully();

        clientShutdownFuture.await();
        serverShutdownFuture.await();
    }

    private static String generateRandomDeviceToken() {
        final byte[] tokenBytes = new byte[TOKEN_LENGTH];
        new Random().nextBytes(tokenBytes);

        final StringBuilder builder = new StringBuilder(TOKEN_LENGTH * 2);

        for (final byte b : tokenBytes) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }
}
