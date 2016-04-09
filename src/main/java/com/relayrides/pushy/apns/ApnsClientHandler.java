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

import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaderNames;
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
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.PromiseCombiner;
import io.netty.util.concurrent.ScheduledFuture;

class ApnsClientHandler<T extends ApnsPushNotification> extends Http2ConnectionHandler {

    private long nextStreamId = 1;

    private final Map<Integer, T> pushNotificationsByStreamId = new HashMap<>();
    private final Map<Integer, Http2Headers> headersByStreamId = new HashMap<>();

    private ApnsClient<T> apnsClient;

    private long nextPingId = new Random().nextLong();
    private ScheduledFuture<?> pingTimeoutFuture;

    private static final int PING_TIMEOUT = 30; // seconds

    private static final String APNS_PATH_PREFIX = "/3/device/";
    private static final AsciiString APNS_EXPIRATION_HEADER = new AsciiString("apns-expiration");
    private static final AsciiString APNS_TOPIC_HEADER = new AsciiString("apns-topic");
    private static final AsciiString APNS_PRIORITY_HEADER = new AsciiString("apns-priority");

    private static final long STREAM_ID_RESET_THRESHOLD = Integer.MAX_VALUE - 1;

    private static final int INITIAL_PAYLOAD_BUFFER_CAPACITY = 4096;

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new DateAsMillisecondsSinceEpochTypeAdapter())
            .create();

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final Logger log = LoggerFactory.getLogger(ApnsClientHandler.class);

    public static class ApnsClientHandlerBuilder<S extends ApnsPushNotification> extends AbstractHttp2ConnectionHandlerBuilder<ApnsClientHandler<S>, ApnsClientHandlerBuilder<S>> {

        private ApnsClient<S> apnsClient;

        public ApnsClientHandlerBuilder<S> apnsClient(final ApnsClient<S> apnsClient) {
            this.apnsClient = apnsClient;
            return this;
        }

        public ApnsClient<S> apnsClient() {
            return this.apnsClient;
        }

        @Override
        public ApnsClientHandlerBuilder<S> server(final boolean isServer) {
            return super.server(isServer);
        }

        @Override
        public ApnsClientHandlerBuilder<S> encoderEnforceMaxConcurrentStreams(final boolean enforceMaxConcurrentStreams) {
            return super.encoderEnforceMaxConcurrentStreams(enforceMaxConcurrentStreams);
        }

        @Override
        public ApnsClientHandler<S> build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final ApnsClientHandler<S> handler = new ApnsClientHandler<>(decoder, encoder, initialSettings, this.apnsClient());
            this.frameListener(handler.new ApnsClientHandlerFrameAdapter());
            return handler;
        }

        @Override
        public ApnsClientHandler<S> build() {
            return super.build();
        }
    }

    private class ApnsClientHandlerFrameAdapter extends Http2FrameAdapter {
        @Override
        public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
            log.trace("Received data from APNs gateway on stream {}: {}", streamId, data.toString(UTF8));

            final int bytesProcessed = data.readableBytes() + padding;

            if (endOfStream) {
                final Http2Headers headers = ApnsClientHandler.this.headersByStreamId.remove(streamId);
                final T pushNotification = ApnsClientHandler.this.pushNotificationsByStreamId.remove(streamId);

                final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));
                final ErrorResponse errorResponse = gson.fromJson(data.toString(UTF8), ErrorResponse.class);

                ApnsClientHandler.this.apnsClient.handlePushNotificationResponse(new SimplePushNotificationResponse<>(
                        pushNotification, success, errorResponse.getReason(), errorResponse.getTimestamp()));
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

            final boolean success = HttpResponseStatus.OK.equals(HttpResponseStatus.parseLine(headers.status()));

            if (endOfStream) {
                if (!success) {
                    log.error("Gateway sent an end-of-stream HEADERS frame for an unsuccessful notification.");
                }

                final T pushNotification = ApnsClientHandler.this.pushNotificationsByStreamId.remove(streamId);

                ApnsClientHandler.this.apnsClient.handlePushNotificationResponse(new SimplePushNotificationResponse<>(
                        pushNotification, success, null, null));
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
            log.info("Received GOAWAY from APNs server: {}", debugData.toString(UTF8));
        }
    }

    protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final ApnsClient<T> apnsClient) {
        super(decoder, encoder, initialSettings);

        this.apnsClient = apnsClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) {
        try {
            // We'll catch class cast issues gracefully
            final T pushNotification = (T) message;

            final int streamId = (int) this.nextStreamId;

            final ByteBuf payloadBuffer = context.alloc().ioBuffer(INITIAL_PAYLOAD_BUFFER_CAPACITY);
            payloadBuffer.writeBytes(pushNotification.getPayload().getBytes(UTF8));

            final Http2Headers headers = new DefaultHttp2Headers()
                    .method("POST")
                    .path(APNS_PATH_PREFIX + pushNotification.getToken())
                    .addInt(HttpHeaderNames.CONTENT_LENGTH, payloadBuffer.readableBytes())
                    .addInt(APNS_EXPIRATION_HEADER, pushNotification.getExpiration() == null ? 0 : (int) (pushNotification.getExpiration().getTime() / 1000));

            if (pushNotification.getPriority() != null) {
                headers.addInt(APNS_PRIORITY_HEADER, pushNotification.getPriority().getCode());
            }

            if (pushNotification.getTopic() != null) {
                headers.add(APNS_TOPIC_HEADER, pushNotification.getTopic());
            }

            final ChannelPromise headersPromise = context.newPromise();
            this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);
            log.trace("Wrote headers on stream {}: {}", streamId, headers);

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
                    } else {
                        log.trace("Failed to write push notification on stream {}.", streamId, future.cause());
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

        } catch (final ClassCastException e) {
            // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it through.
            log.error("Unexpected object in pipeline: {}", message);
            context.write(message, writePromise);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext context, final Object event) throws Exception {
        if (event instanceof IdleStateEvent) {

            assert PING_TIMEOUT < ApnsClient.PING_IDLE_TIME;

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

            context.flush();
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
}
