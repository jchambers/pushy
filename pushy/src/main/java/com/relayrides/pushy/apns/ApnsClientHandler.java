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

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.concurrent.ScheduledFuture;

class ApnsClientHandler extends Http2ConnectionHandler {

    private long nextStreamId = 1;

    private final Map<Integer, ApnsPushNotification> pushNotificationsByStreamId = new HashMap<>();
    private final Map<Integer, Http2Headers> headersByStreamId = new HashMap<>();

    private final ApnsKeyRegistry<ApnsSigningKey> signingKeyRegistry;
    private final Map<String, String> encodedAuthenticationTokensByTopic = new HashMap<>();
    private final Map<ApnsPushNotification, String> encodedAuthenticationTokensByPushNotification = new WeakHashMap<>();

    private final Map<ApnsPushNotification, Promise<PushNotificationResponse<ApnsPushNotification>>> responsePromises =
            new IdentityHashMap<>();

    private final String authority;

    private long nextPingId = new Random().nextLong();
    private ScheduledFuture<?> pingTimeoutFuture;

    private static final int PING_TIMEOUT = 30; // seconds

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");
    private static final AsciiString APNS_COLLAPSE_ID_HEADER = new AsciiString("apns-collapse-id");
    private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

    private static final long STREAM_ID_RESET_THRESHOLD = Integer.MAX_VALUE - 1;

    private static final int INITIAL_PAYLOAD_BUFFER_CAPACITY = 4096;

    private static final String EXPIRED_AUTH_TOKEN_REASON = "ExpiredProviderToken";

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsTimeSinceEpochTypeAdapter(TimeUnit.MILLISECONDS))
            .create();

    private static final Logger log = LoggerFactory.getLogger(ApnsClientHandler.class);

    public static class ApnsClientHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<ApnsClientHandler, ApnsClientHandlerBuilder> {

        private String authority;
        private ApnsKeyRegistry<ApnsSigningKey> signingKeyRegistry;

        public ApnsClientHandlerBuilder authority(final String authority) {
            this.authority = authority;
            return this;
        }

        public String authority() {
            return this.authority;
        }

        public ApnsClientHandlerBuilder signingKeyRegistry(final ApnsKeyRegistry<ApnsSigningKey> signingKeyRegistry) {
            this.signingKeyRegistry = signingKeyRegistry;
            return this;
        }

        public ApnsKeyRegistry<ApnsSigningKey> signingKeyRegistry() {
            return this.signingKeyRegistry;
        }

        @Override
        public ApnsClientHandlerBuilder server(final boolean isServer) {
            return super.server(isServer);
        }

        @Override
        public ApnsClientHandlerBuilder encoderEnforceMaxConcurrentStreams(final boolean enforceMaxConcurrentStreams) {
            return super.encoderEnforceMaxConcurrentStreams(enforceMaxConcurrentStreams);
        }

        @Override
        public ApnsClientHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            Objects.requireNonNull(this.authority(), "Authority must be set before building an ApnsClientHandler.");

            final ApnsClientHandler handler = new ApnsClientHandler(decoder, encoder, initialSettings, this.authority(), this.signingKeyRegistry());
            this.frameListener(handler.new ApnsClientHandlerFrameAdapter());

            return handler;
        }

        @Override
        public ApnsClientHandler build() {
            return super.build();
        }
    }

    private class ApnsClientHandlerFrameAdapter extends Http2FrameAdapter {
        @Override
        public void onSettingsRead(final ChannelHandlerContext context, final Http2Settings settings) {
            log.trace("Received settings from APNs gateway: {}", settings);
        }

        @Override
        public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
            log.trace("Received data from APNs gateway on stream {}: {}", streamId, data.toString(StandardCharsets.UTF_8));

            final int bytesProcessed = data.readableBytes() + padding;

            if (endOfStream) {
                final Http2Headers headers = ApnsClientHandler.this.headersByStreamId.remove(streamId);
                final ApnsPushNotification pushNotification = ApnsClientHandler.this.pushNotificationsByStreamId.remove(streamId);
                final String encodedAuthenticationToken = ApnsClientHandler.this.encodedAuthenticationTokensByPushNotification.get(pushNotification);

                final HttpResponseStatus status = HttpResponseStatus.parseLine(headers.status());
                final String responseBody = data.toString(StandardCharsets.UTF_8);

                if (HttpResponseStatus.INTERNAL_SERVER_ERROR.equals(status)) {
                    log.warn("APNs server reported an internal error when sending {}.", pushNotification);
                    ApnsClientHandler.this.responsePromises.get(pushNotification).tryFailure(new ApnsServerException(responseBody));
                } else {
                    final ErrorResponse errorResponse = gson.fromJson(responseBody, ErrorResponse.class);

                    if (ApnsClientHandler.EXPIRED_AUTH_TOKEN_REASON.equals(errorResponse.getReason())) {
                        if (encodedAuthenticationToken == null || encodedAuthenticationToken.equals(ApnsClientHandler.this.encodedAuthenticationTokensByTopic.get(pushNotification.getTopic()))) {
                            // We only want to clear this if the token that expired is actually the one we have in our
                            // map; otherwise, we might wind up needlessly regenerating tokens a zillion times.
                            ApnsClientHandler.this.encodedAuthenticationTokensByTopic.remove(pushNotification.getTopic());
                        }

                        // Once we've invalidated an expired token, it's reasonable to expect that re-sending the
                        // notification will succeed.
                        context.channel().write(pushNotification);
                    } else {
                        ApnsClientHandler.this.responsePromises.get(pushNotification).trySuccess(
                                new SimplePushNotificationResponse<>(pushNotification, HttpResponseStatus.OK.equals(status), errorResponse.getReason(), errorResponse.getTimestamp()));
                    }
                }
            } else {
                log.error("Gateway sent a DATA frame that was not the end of a stream.");
            }

            return bytesProcessed;
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {
            this.onHeadersRead(context, streamId, headers, padding, endOfStream);
        }

        @Override
        public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
            log.trace("Received headers from APNs gateway on stream {}: {}", streamId, headers);

            if (endOfStream) {
                final HttpResponseStatus status = HttpResponseStatus.parseLine(headers.status());
                final boolean success = HttpResponseStatus.OK.equals(status);

                if (!success) {
                    log.warn("Gateway sent an end-of-stream HEADERS frame for an unsuccessful notification.");
                }

                final ApnsPushNotification pushNotification = ApnsClientHandler.this.pushNotificationsByStreamId.remove(streamId);

                if (HttpResponseStatus.INTERNAL_SERVER_ERROR.equals(status)) {
                    log.warn("APNs server reported an internal error when sending {}.", pushNotification);
                    ApnsClientHandler.this.responsePromises.get(pushNotification).tryFailure(new ApnsServerException(null));
                } else {
                    ApnsClientHandler.this.responsePromises.get(pushNotification).trySuccess(
                            new SimplePushNotificationResponse<>(pushNotification, success, null, null));
                }
            } else {
                ApnsClientHandler.this.headersByStreamId.put(streamId, headers);
            }
        }

        @Override
        public void onPingAckRead(final ChannelHandlerContext context, final ByteBuf data) {
            if (ApnsClientHandler.this.pingTimeoutFuture != null) {
                log.trace("Received reply to ping.");
                ApnsClientHandler.this.pingTimeoutFuture.cancel(false);
            } else {
                log.error("Received PING ACK, but no corresponding outbound PING found.");
            }
        }

        @Override
        public void onGoAwayRead(final ChannelHandlerContext context, final int lastStreamId, final long errorCode, final ByteBuf debugData) throws Http2Exception {
            log.info("Received GOAWAY from APNs server: {}", debugData.toString(StandardCharsets.UTF_8));
        }
    }

    protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final String authority, final ApnsKeyRegistry<ApnsSigningKey> signingKeyRegistry) {
        super(decoder, encoder, initialSettings);

        this.authority = authority;
        this.signingKeyRegistry = signingKeyRegistry;
    }

    @Override
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) throws Http2Exception, SignatureException, NoKeyForTopicException {
        if (message instanceof PushNotificationAndResponsePromise) {
            final PushNotificationAndResponsePromise pushNotificationAndResponsePromise =
                    (PushNotificationAndResponsePromise) message;

            final ApnsPushNotification pushNotification = pushNotificationAndResponsePromise.getPushNotification();

            if (this.responsePromises.containsKey(pushNotification)) {
                writePromise.tryFailure(new PushNotificationStillPendingException());
            } else {
                this.responsePromises.put(pushNotification, pushNotificationAndResponsePromise.getResponsePromise());

                pushNotificationAndResponsePromise.getResponsePromise().addListener(new GenericFutureListener<Future<PushNotificationResponse<ApnsPushNotification>>> () {

                    @Override
                    public void operationComplete(final Future<PushNotificationResponse<ApnsPushNotification>> future) {
                        // Regardless of the outcome, when the response promise is finished, we want to remove it from
                        // the map of pending promises.
                        ApnsClientHandler.this.responsePromises.remove(pushNotification);
                    }
                });

                this.write(context, pushNotification, writePromise);
            }
        } else if (message instanceof ApnsPushNotification) {
            final ApnsPushNotification pushNotification = (ApnsPushNotification) message;

            final String encodedAuthenticationToken = this.getEncodedAuthenticationTokenForTopic(pushNotification.getTopic());

            final int streamId = (int) this.nextStreamId;

            final Http2Headers headers = new DefaultHttp2Headers()
                    .method(HttpMethod.POST.asciiName())
                    .authority(this.authority)
                    .path(APNS_PATH_PREFIX + pushNotification.getToken())
                    .addInt(APNS_EXPIRATION_HEADER, pushNotification.getExpiration() == null ? 0 : (int) (pushNotification.getExpiration().getTime() / 1000))
                    .add(APNS_AUTHORIZATION_HEADER, "bearer " + encodedAuthenticationToken);

            if (pushNotification.getCollapseId() != null) {
                headers.add(APNS_COLLAPSE_ID_HEADER, pushNotification.getCollapseId());
            }

            if (pushNotification.getPriority() != null) {
                headers.addInt(APNS_PRIORITY_HEADER, pushNotification.getPriority().getCode());
            }

            if (pushNotification.getTopic() != null) {
                headers.add(APNS_TOPIC_HEADER, pushNotification.getTopic());
            }

            final ChannelPromise headersPromise = context.newPromise();
            this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);
            log.trace("Wrote headers on stream {}: {}", streamId, headers);

            final ByteBuf payloadBuffer = context.alloc().ioBuffer(INITIAL_PAYLOAD_BUFFER_CAPACITY);
            payloadBuffer.writeBytes(pushNotification.getPayload().getBytes(StandardCharsets.UTF_8));

            final ChannelPromise dataPromise = context.newPromise();
            this.encoder().writeData(context, streamId, payloadBuffer, 0, true, dataPromise);
            log.trace("Wrote payload on stream {}: {}", streamId, pushNotification.getPayload());

            final PromiseCombiner promiseCombiner = new PromiseCombiner();
            promiseCombiner.addAll(headersPromise, dataPromise);
            promiseCombiner.finish(writePromise);

            writePromise.addListener(new GenericFutureListener<ChannelPromise>() {

                @Override
                public void operationComplete(final ChannelPromise future) throws Exception {
                    if (future.isSuccess()) {
                        ApnsClientHandler.this.pushNotificationsByStreamId.put(streamId, pushNotification);
                        ApnsClientHandler.this.encodedAuthenticationTokensByPushNotification.put(pushNotification, encodedAuthenticationToken);
                    } else {
                        log.trace("Failed to write push notification on stream {}.", streamId, future.cause());

                        final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise =
                                ApnsClientHandler.this.responsePromises.get(pushNotification);

                        if (responsePromise != null) {
                            responsePromise.tryFailure(future.cause());
                        } else {
                            log.error("Notification write failed, but no response promise found.");
                        }
                    }
                }
            });

            this.nextStreamId += 2;

            if (this.nextStreamId >= STREAM_ID_RESET_THRESHOLD) {
                // This is very unlikely, but in the event that we run out of stream IDs (the maximum allowed is
                // 2^31, per https://httpwg.github.io/specs/rfc7540.html#StreamIdentifiers), we need to open a new
                // connection. Just closing the context should be enough; automatic reconnection should take things
                // from there.
                context.close();
            }
        } else {
            // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it through.
            log.error("Unexpected object in pipeline: {}", message);
            context.write(message, writePromise);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
        if (event instanceof IdleStateEvent) {
            assert PING_TIMEOUT < ApnsClient.PING_IDLE_TIME_MILLIS;

            log.trace("Sending ping due to inactivity.");

            final ByteBuf pingDataBuffer = context.alloc().ioBuffer(8, 8);
            pingDataBuffer.writeLong(this.nextPingId++);

            this.encoder().writePing(context, false, pingDataBuffer, context.newPromise()).addListener(new GenericFutureListener<ChannelFuture>() {

                @Override
                public void operationComplete(final ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ApnsClientHandler.this.pingTimeoutFuture = future.channel().eventLoop().schedule(new Runnable() {

                            @Override
                            public void run() {
                                log.debug("Closing channel due to ping timeout.");
                                future.channel().close();
                            }
                        }, PING_TIMEOUT, TimeUnit.SECONDS);
                    } else {
                        log.debug("Failed to write PING frame.", future.cause());
                        future.channel().close();
                    }
                }
            });

            this.flush(context);
        } else if (event instanceof SigningKeyRemovalEvent) {
            final SigningKeyRemovalEvent signingKeyRemovalEvent = (SigningKeyRemovalEvent) event;

            for (final String topic : signingKeyRemovalEvent.getTopicsToClear()) {
                this.encodedAuthenticationTokensByTopic.remove(topic);
            }
        }

        super.userEventTriggered(context, event);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) throws Exception {
        if (cause instanceof WriteTimeoutException) {
            log.debug("Closing connection due to write timeout.");
            context.close();
        } else {
            log.warn("APNs client pipeline caught an exception.", cause);
        }
    }

    @Override
    public void close(final ChannelHandlerContext context, final ChannelPromise promise) throws Exception {
        super.close(context, promise);

        final ClientNotConnectedException clientNotConnectedException =
                new ClientNotConnectedException("Client disconnected unexpectedly.");

        for (final Promise<PushNotificationResponse<ApnsPushNotification>> responsePromise : this.responsePromises.values()) {
            responsePromise.tryFailure(clientNotConnectedException);
        }

        this.responsePromises.clear();
    }

    private String getEncodedAuthenticationTokenForTopic(final String topic) throws NoKeyForTopicException {
        if (!this.encodedAuthenticationTokensByTopic.containsKey(topic)) {
            final ApnsSigningKey key = this.signingKeyRegistry.getKeyForTopic(topic);

            if (key == null) {
                throw new NoKeyForTopicException("No key found for topic: " + topic);
            }

            try {
                this.encodedAuthenticationTokensByTopic.put(topic, new AuthenticationToken(key, new Date()).toString());
            } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
                // This should never happen in practice because we check both the availability of the algorithm and the
                // validity of the key when the key is added to the registry.
                throw new RuntimeException(e);
            }
        }

        return this.encodedAuthenticationTokensByTopic.get(topic);
    }
}
