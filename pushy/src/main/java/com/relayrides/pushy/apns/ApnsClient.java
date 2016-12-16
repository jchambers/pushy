/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.proxy.ProxyHandlerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

/**
 * <p>An APNs client sends push notifications to the APNs gateway. APNs clients communicate with an APNs server using
 * HTTP/2 over a TLS-protected connection. Clients include an authentication token with each notification they send;
 * authentication tokens are cryptographically-signed with a signing key, and clients may send notifications to any
 * "topic" for which they have a key. Please see Apple's
 * <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/APNSOverview.html">Local
 * and Remote Notification Programming Guide</a> for a detailed discussion of the APNs protocol, topics, and key
 * provisioning.</p>
 *
 * <p>Clients are constructed using an {@link ApnsClientBuilder}. Callers may
 * optionally specify an {@link EventLoopGroup} when constructing a new client. If no event loop group is specified,
 * clients will create and manage their own single-thread event loop group. If many clients are operating in parallel,
 * specifying a shared event loop group serves as a mechanism to keep the total number of threads in check. Callers may
 * also want to provide a specific event loop group to take advantage of platform-specific features (i.e.
 * {@code epoll}).</p>
 *
 * <p>Callers must register signing keys for the topics to which the client will send notifications using one of the
 * {@code registerSigningKey} methods. Callers may register keys at any time after a client has been constructed.</p>
 *
 * <p>Once a client has been constructed, it must connect to an APNs server before it can begin sending push
 * notifications. Apple provides a production and development gateway; see {@link ApnsClient#PRODUCTION_APNS_HOST} and
 * {@link ApnsClient#DEVELOPMENT_APNS_HOST}. See the
 * <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating
 * with APNs</a> documentation for additional details.</p>
 *
 * <p>Once a connection has been established, an APNs client will attempt to restore that connection automatically if
 * the connection closes unexpectedly. APNs clients employ an exponential back-off strategy to manage the rate of
 * reconnection attempts. Clients will stop trying to reconnect automatically if disconnected via the
 * {@link ApnsClient#disconnect()} method.</p>
 *
 * <p>Notifications sent by a client to an APNs server are sent asynchronously. A
 * {@link io.netty.util.concurrent.Future io.netty.util.concurrent.Future} is returned immediately when a notification
 * is sent, but will not complete until the attempt to send the notification has failed, the notification has been
 * accepted by the APNs server, or the notification has been rejected by the APNs server. Please note that the
 * {@code Future} returned is a {@code io.netty.util.concurrent.Future}, which is an extension of the
 * {@link java.util.concurrent.Future java.util.concurrent.Future} interface that allows callers to attach listeners
 * that will be notified when the {@code Future} completes.</p>
 *
 * <p>APNs clients are intended to be long-lived, persistent resources. Callers should shut them down when they are no
 * longer needed (i.e. when shutting down the entire application). If an event loop group was specified at construction
 * time, callers should shut down that event loop group when all clients using that group have been disconnected.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.5
 */
public class ApnsClient {
    private final ApnsChannelPool channelPool;

    private final boolean shouldShutDownEventLoopGroup;
    private volatile boolean isShutDown;

    private long reconnectDelaySeconds = INITIAL_RECONNECT_DELAY_SECONDS;

    private final Map<ApnsPushNotification, Promise<PushNotificationResponse<ApnsPushNotification>>> responsePromises =
            new IdentityHashMap<>();

    private ApnsClientMetricsListener metricsListener = new NoopMetricsListener();
    private final AtomicLong nextNotificationId = new AtomicLong(0);

    private final ReadWriteLock authenticationTokenLock = new ReentrantReadWriteLock();
    private final Map<String, Set<String>> topicsByTeamId = new HashMap<>();
    private final Map<String, AuthenticationTokenSupplier> authenticationTokenSuppliersByTopic = new HashMap<>();

    /**
     * The default write timeout, in milliseconds.
     *
     * @since 0.6
     */
    public static final long DEFAULT_WRITE_TIMEOUT_MILLIS = 20_000;

    /**
     * The hostname for the production APNs gateway.
     *
     * @since 0.5
     */
    public static final String PRODUCTION_APNS_HOST = "api.push.apple.com";

    /**
     * The hostname for the development APNs gateway.
     *
     * @since 0.5
     */
    public static final String DEVELOPMENT_APNS_HOST = "api.development.push.apple.com";

    /**
     * The default (HTTPS) port for communication with the APNs gateway.
     *
     * @since 0.5
     */
    public static final int DEFAULT_APNS_PORT = 443;

    /**
     * <p>An alternative port for communication with the APNs gateway. According to Apple's documentation:</p>
     *
     * <blockquote>You can alternatively use port 2197 when communicating with APNs. You might do this, for example, to
     * allow APNs traffic through your firewall but to block other HTTPS traffic.</blockquote>
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/NetworkingInternet/Conceptual/RemoteNotificationsPG/CommunicatingwithAPNs.html#//apple_ref/doc/uid/TP40008194-CH11-SW1">Communicating
     * with APNs</a>
     *
     * @since 0.5
     */
    public static final int ALTERNATE_APNS_PORT = 2197;

    private static final ClientBusyException CLIENT_BUSY_EXCEPTION = new ClientBusyException();
    private static final IllegalStateException NOTIFICATION_ALREADY_PENDING_EXCEPTION =
            new IllegalStateException("The given notification has already been sent and not yet resolved.");

    private static final long INITIAL_RECONNECT_DELAY_SECONDS = 1; // second
    private static final long MAX_RECONNECT_DELAY_SECONDS = 60; // seconds
    static final int PING_IDLE_TIME_MILLIS = 60_000; // milliseconds

    static final String EXPIRED_AUTH_TOKEN_REASON = "ExpiredProviderToken";

    private static final Logger log = LoggerFactory.getLogger(ApnsClient.class);

    protected ApnsClient(final SocketAddress apnsServerAddress, final SslContext sslContext, final EventLoopGroup eventLoopGroup, final int maxChannels) {
        this.shouldShutDownEventLoopGroup = (eventLoopGroup == null);
        this.channelPool = new ApnsChannelPool(this, apnsServerAddress, sslContext, eventLoopGroup != null ? eventLoopGroup : new NioEventLoopGroup(1), maxChannels);
    }

    /**
     * Sets the maximum amount of time, in milliseconds, that a client will wait to establish a connection with the
     * APNs server before the connection attempt is considered a failure.
     *
     * @param timeoutMillis the maximum amount of time in milliseconds to wait for a connection attempt to complete
     *
     * @since 0.5
     */
    protected void setConnectionTimeout(final int timeoutMillis) {
        this.channelPool.setConnectionTimeout(timeoutMillis);
    }

    /**
     * Sets the buffer usage watermark range for this client. When a the amount of buffered and not-yet-flushed data in
     * the client's network channel exceeds the given "high-water" mark, the channel will begin rejecting new data until
     * enough data has been flushed to cross the given "low-water" mark. Notifications sent when the client's network
     * channel is "flooded" will fail with a {@link ClientBusyException}.
     *
     * @param writeBufferWatermark the buffer usage watermark range for the client's network channel
     *
     * @since 0.8.2
     */
    protected void setChannelWriteBufferWatermark(final WriteBufferWaterMark writeBufferWaterMark) {
        this.channelPool.setChannelWriteBufferWatermark(writeBufferWaterMark);
    }

    /**
     * Sets the metrics listener for this client. Metrics listeners gather information that describes the performance
     * and behavior of a client, and are completely optional.
     *
     * @param metricsListener the metrics listener for this client, or {@code null} if this client should not report
     * metrics to a listener
     *
     * @since 0.6
     */
    protected void setMetricsListener(final ApnsClientMetricsListener metricsListener) {
        this.metricsListener = metricsListener != null ? metricsListener : new NoopMetricsListener();
    }

    /**
     * Sets the proxy handler factory to be used to construct proxy handlers when establishing a new connection to the
     * APNs gateway. Proxy handlers are added to the beginning of the client's pipeline. A client's proxy handler
     * factory may be {@code null}, in which case the client will connect to the gateway directly and will not use a
     * proxy. By default, clients will not use a proxy.
     *
     * @param proxyHandlerFactory the proxy handler factory to be used to construct proxy handlers, or {@code null} if
     * this client should not use a proxy
     *
     * @since 0.6
     */
    protected void setProxyHandlerFactory(final ProxyHandlerFactory proxyHandlerFactory) {
        this.channelPool.setProxyHandlerFactory(proxyHandlerFactory);
    }

    /**
     * <p>Sets the write timeout for this client. If an attempt to send a notification to the APNs server takes longer
     * than the given timeout, the connection will be closed (and automatically reconnected later). Note that write
     * timeouts refer to the amount of time taken to <em>send</em> a notification to the server, and not the time taken
     * by the server to process and respond to a notification.</p>
     *
     * <p>Write timeouts should generally be set before starting a connection attempt. Changes to a client's write
     * timeout will take effect after the next connection attempt; changes made to an already-connected client will have
     * no immediate effect.</p>
     *
     * <p>By default, clients have a write timeout of
     * {@value com.relayrides.pushy.apns.ApnsClient#DEFAULT_WRITE_TIMEOUT_MILLIS} milliseconds.</p>
     *
     * @param writeTimeoutMillis the write timeout for this client in milliseconds; if zero, write attempts will never
     * time out
     *
     * @since 0.6
     */
    protected void setWriteTimeout(final long writeTimeoutMillis) {
        this.channelPool.setWriteTimeout(writeTimeoutMillis);
    }

    /**
     * Sets the amount of time (in milliseconds) clients should wait for in-progress requests to complete before closing
     * a connection during a graceful shutdown.
     *
     * @param timeoutMillis the number of milliseconds to wait for in-progress requests to complete before closing a
     * connection
     *
     * @see ApnsClient#disconnect()
     *
     * @since 0.5
     */
    protected void setGracefulShutdownTimeout(final long timeoutMillis) {
        this.channelPool.setGracefulShutdownTimeout(timeoutMillis);
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyPemFile a PEM file that contains a PKCS#8-formatted elliptic-curve private key with which to
     * sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given file for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final File signingKeyPemFile, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        this.registerSigningKey(signingKeyPemFile, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyPemFile a PEM file that contains a PKCS#8-formatted elliptic-curve private key with which to
     * sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given file for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final File signingKeyPemFile, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        try (final FileInputStream signingKeyInputStream = new FileInputStream(signingKeyPemFile)) {
            this.registerSigningKey(signingKeyInputStream, teamId, keyId, topics);
        }
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyInputStream an input stream that provides a PEM-encoded, PKCS#8-formatted elliptic-curve private
     * key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given input stream for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final InputStream signingKeyInputStream, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        this.registerSigningKey(signingKeyInputStream, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKeyInputStream an input stream that provides a PEM-encoded, PKCS#8-formatted elliptic-curve private
     * key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     * @throws IOException if a private key could not be loaded from the given input stream for any reason
     *
     * @since 0.9
     */
    public void registerSigningKey(final InputStream signingKeyInputStream, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        final ECPrivateKey signingKey;
        {
            final String base64EncodedPrivateKey;
            {
                final StringBuilder privateKeyBuilder = new StringBuilder();

                final BufferedReader reader = new BufferedReader(new InputStreamReader(signingKeyInputStream));
                boolean haveReadHeader = false;
                boolean haveReadFooter = false;

                for (String line; (line = reader.readLine()) != null; ) {
                    if (!haveReadHeader) {
                        if (line.contains("BEGIN PRIVATE KEY")) {
                            haveReadHeader = true;
                            continue;
                        }
                    } else {
                        if (line.contains("END PRIVATE KEY")) {
                            haveReadFooter = true;
                            break;
                        } else {
                            privateKeyBuilder.append(line);
                        }
                    }
                }

                if (!(haveReadHeader && haveReadFooter)) {
                    throw new IOException("Could not find private key header/footer");
                }

                base64EncodedPrivateKey = privateKeyBuilder.toString();
            }

            final ByteBuf wrappedEncodedPrivateKey = Unpooled.wrappedBuffer(base64EncodedPrivateKey.getBytes(StandardCharsets.US_ASCII));

            try {
                final ByteBuf decodedPrivateKey = Base64.decode(wrappedEncodedPrivateKey);

                try {
                    final byte[] keyBytes = new byte[decodedPrivateKey.readableBytes()];
                    decodedPrivateKey.readBytes(keyBytes);

                    final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
                    final KeyFactory keyFactory = KeyFactory.getInstance("EC");
                    signingKey = (ECPrivateKey) keyFactory.generatePrivate(keySpec);
                } catch (final InvalidKeySpecException e) {
                    throw new InvalidKeyException(e);
                } finally {
                    decodedPrivateKey.release();
                }
            } finally {
                wrappedEncodedPrivateKey.release();
            }
        }

        this.registerSigningKey(signingKey, teamId, keyId, topics);
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKey the private key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     *
     * @since 0.9
     */
    public void registerSigningKey(final ECPrivateKey signingKey, final String teamId, final String keyId, final Collection<String> topics) throws InvalidKeyException, NoSuchAlgorithmException {
        this.registerSigningKey(signingKey, teamId, keyId, topics.toArray(new String[0]));
    }

    /**
     * <p>Registers a private signing key for the given topics. Clears any topics and keys previously associated with
     * the given team.</p>
     *
     * <p>Callers <em>must</em> register signing keys for all topics to which they intend to send notifications. Tokens
     * may be registered at any time in a client's life-cycle.</p>
     *
     * @param signingKey the private key with which to sign authentication tokens
     * @param teamId the Apple-issued, ten-character identifier for the team to which the given private key belongs
     * @param keyId the Apple-issued, ten-character identifier for the given private key
     * @param topics the topics to which the given signing key is applicable
     *
     * @throws InvalidKeyException if the given key is invalid for any reason
     * @throws NoSuchAlgorithmException if the JRE does not support the required token-signing algorithm
     *
     * @since 0.9
     */
    public void registerSigningKey(final ECPrivateKey signingKey, final String teamId, final String keyId, final String... topics) throws InvalidKeyException, NoSuchAlgorithmException {
        this.authenticationTokenLock.writeLock().lock();

        try {
            this.removeKeyForTeam(teamId);

            final AuthenticationTokenSupplier tokenSupplier = new AuthenticationTokenSupplier(teamId, keyId, signingKey);

            final Set<String> topicSet = new HashSet<>();

            for (final String topic : topics) {
                topicSet.add(topic);
                this.authenticationTokenSuppliersByTopic.put(topic, tokenSupplier);
            }

            this.topicsByTeamId.put(teamId, topicSet);
        } finally {
            this.authenticationTokenLock.writeLock().unlock();
        }
    }

    /**
     * Removes all registered keys and associated topics for the given team.
     *
     * @param teamId the Apple-issued, ten-character identifier for the team for which to remove keys and topics
     */
    public void removeKeyForTeam(final String teamId) {
        this.authenticationTokenLock.writeLock().lock();

        try {
            final Set<String> oldTopics = this.topicsByTeamId.remove(teamId);

            if (oldTopics != null) {
                for (final String topic : oldTopics) {
                    this.authenticationTokenSuppliersByTopic.remove(topic);
                }
            }
        } finally {
            this.authenticationTokenLock.writeLock().unlock();
        }
    }

    protected AuthenticationTokenSupplier getAuthenticationTokenSupplierForTopic(final String topic) throws NoKeyForTopicException {
        final AuthenticationTokenSupplier supplier;

        this.authenticationTokenLock.readLock().lock();

        try {
            supplier = this.authenticationTokenSuppliersByTopic.get(topic);
        } finally {
            this.authenticationTokenLock.readLock().unlock();
        }

        if (supplier == null) {
            throw new NoKeyForTopicException("No signing key found for topic " + topic);
        }

        return supplier;
    }

    /**
     * <p>Sends a push notification to the APNs gateway.</p>
     *
     * <p>This method returns a {@code Future} that indicates whether the notification was accepted or rejected by the
     * gateway. If the notification was accepted, it may be delivered to its destination device at some time in the
     * future, but final delivery is not guaranteed. Rejections should be considered permanent failures, and callers
     * should <em>not</em> attempt to re-send the notification.</p>
     *
     * <p>The returned {@code Future} may fail with an exception if the notification could not be sent. Failures to
     * <em>send</em> a notification to the gateway—i.e. those that fail with exceptions—should generally be considered
     * non-permanent, and callers should attempt to re-send the notification when the underlying problem has been
     * resolved.</p>
     *
     * <p>In particular, attempts to send a notification when the client is not connected will fail with a
     * {@link ClientNotConnectedException}. If the client was previously connected and has not been explicitly
     * disconnected (via the {@link ApnsClient#disconnect()} method), the client will attempt to reconnect
     * automatically. Callers may wait for a reconnection attempt to complete by waiting for the {@code Future} returned
     * by the {@link ApnsClient#getReconnectionFuture()} method.</p>
     *
     * @param notification the notification to send to the APNs gateway
     *
     * @param <T> the type of notification to be sent
     *
     * @return a {@code Future} that will complete when the notification has been either accepted or rejected by the
     * APNs gateway
     *
     * @since 0.8
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public <T extends ApnsPushNotification> Future<PushNotificationResponse<T>> sendNotification(final T notification) {
        final Promise<PushNotificationResponse<T>> promise = new DefaultPromise<>(this.channelPool.getEventLoopGroup().next());

        if (this.isShutDown) {
            return promise.setFailure(new IllegalStateException("Client has shut down."));
        }

        synchronized (this.responsePromises) {
            if (this.responsePromises.containsKey(notification)) {
                return promise.setFailure(NOTIFICATION_ALREADY_PENDING_EXCEPTION);
            } else {
                this.responsePromises.put(notification, (Promise) promise);
            }
        }

        final long notificationId = this.nextNotificationId.getAndIncrement();

        promise.addListener(new GenericFutureListener<Future<PushNotificationResponse<T>>> () {
            @Override
            public void operationComplete(final Future<PushNotificationResponse<T>> future) throws Exception {
                synchronized (ApnsClient.this.responsePromises) {
                    ApnsClient.this.responsePromises.remove(notification);
                }

                if (future.isSuccess()) {
                    final PushNotificationResponse<T> response = future.getNow();

                    if (response.isAccepted()) {
                        ApnsClient.this.metricsListener.handleNotificationAccepted(ApnsClient.this, notificationId);
                    } else {
                        ApnsClient.this.metricsListener.handleNotificationRejected(ApnsClient.this, notificationId);
                    }
                } else {
                    ApnsClient.this.metricsListener.handleWriteFailure(ApnsClient.this, notificationId);
                }
            }
        });

        return this.sendNotification(notification, promise, notificationId);
    }

    private <T extends ApnsPushNotification> Future<PushNotificationResponse<T>> sendNotification(final T notification, final Promise<PushNotificationResponse<T>> promise, final long notificationId) {
        final Future<Channel> channelFuture = this.channelPool.acquire();

        channelFuture.addListener(new GenericFutureListener<Future<Channel>>() {

            @Override
            public void operationComplete(final Future<Channel> future) throws Exception {
                if (future.isSuccess()) {
                    final Channel channel = future.getNow();

                    try {
                        if (channel.isWritable()) {
                            channel.writeAndFlush(notification).addListener(new GenericFutureListener<ChannelFuture>() {

                                @Override
                                public void operationComplete(final ChannelFuture future) throws Exception {
                                    if (future.isSuccess()) {
                                        ApnsClient.this.metricsListener.handleNotificationSent(ApnsClient.this, notificationId);
                                    } else {
                                        log.debug("Failed to write push notification: {}", notification, future.cause());
                                        promise.tryFailure(future.cause());
                                    }
                                }
                            });
                        } else {
                            promise.tryFailure(CLIENT_BUSY_EXCEPTION);
                        }
                    } finally {
                        ApnsClient.this.channelPool.release(channel);
                    }
                } else {
                    promise.tryFailure(future.cause());
                }
            }
        });

        return promise;
    }

    protected void handlePushNotificationResponse(final PushNotificationResponse<ApnsPushNotification> response) {
        log.debug("Received response from APNs gateway: {}", response);

        final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise;

        synchronized (this.responsePromises) {
            responsePromise = this.responsePromises.remove(response.getPushNotification());
        }

        if (EXPIRED_AUTH_TOKEN_REASON.equals(response.getRejectionReason())) {
            synchronized (this.responsePromises) {
                this.responsePromises.put(response.getPushNotification(), responsePromise);
            }

            this.sendNotification(response.getPushNotification(), responsePromise, this.nextNotificationId.getAndIncrement());
        } else {
            responsePromise.setSuccess(response);
        }
    }

    protected void handleServerError(final ApnsPushNotification pushNotification, final String message) {
        log.warn("APNs server reported an internal error when sending {}.", pushNotification);

        synchronized (this.responsePromises) {
            this.responsePromises.remove(pushNotification).tryFailure(new ApnsServerException(message));
        }
    }

    /**
     * <p>Gracefully disconnects from the APNs gateway. The disconnection process will wait until notifications that
     * have been sent to the APNs server have been either accepted or rejected. Note that some notifications passed to
     * {@link com.relayrides.pushy.apns.ApnsClient#sendNotification(ApnsPushNotification)} may still be enqueued and
     * not yet sent by the time the shutdown process begins; the {@code Futures} associated with those notifications
     * will fail.</p>
     *
     * <p>The returned {@code Future} will be marked as complete when the connection has closed completely. If the
     * connection is already closed when this method is called, the returned {@code Future} will be marked as complete
     * immediately.</p>
     *
     * <p>If a non-null {@code EventLoopGroup} was provided at construction time, clients may be reconnected and reused
     * after they have been disconnected. If no event loop group was provided at construction time, clients may not be
     * restarted after they have been disconnected via this method.</p>
     *
     * @return a {@code Future} that will be marked as complete when the connection has been closed
     *
     * @since 0.5
     */
    public Future<Void> disconnect() {
        log.info("Disconnecting.");

        this.isShutDown = true;
        return this.channelPool.shutdown();
    }
}
