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
import com.eatthepath.pushy.apns.auth.AuthenticationToken;
import com.eatthepath.uuid.FastUUID;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.*;
import io.netty.util.AsciiString;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseCombiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class ApnsChannelManagementHandler extends Http2ConnectionHandler implements Http2FrameListener, Http2Connection.Listener {

  private final Map<Integer, ApnsChannelManagementRequest> unattachedRequestsByStreamId = new IntObjectHashMap<>();

  private final Http2Connection.PropertyKey responseHeadersPropertyKey;
  private final Http2Connection.PropertyKey responseDataPropertyKey;
  private final Http2Connection.PropertyKey requestPropertyKey;
  private final Http2Connection.PropertyKey streamErrorCausePropertyKey;

  private final ApnsSigningKey signingKey;
  private AuthenticationToken authenticationToken;

  private final Duration tokenExpiration;
  private ScheduledFuture<?> tokenExpirationFuture;

  private final String authority;

  private Throwable connectionErrorCause;

  static final AsciiString APNS_REQUEST_ID_HEADER = new AsciiString("apns-request-id");
  private static final AsciiString APNS_AUTHORIZATION_HEADER = new AsciiString("authorization");

  private static final IOException STREAMS_EXHAUSTED_EXCEPTION =
      new IOException("HTTP/2 streams exhausted; closing connection.");

  private static final IOException STREAM_CLOSED_BEFORE_REPLY_EXCEPTION =
      new IOException("Stream closed before a reply was received");

  private static final Logger log = LoggerFactory.getLogger(ApnsChannelManagementHandler.class);

  public static class ApnsChannelManagementHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<ApnsChannelManagementHandler, ApnsChannelManagementHandlerBuilder> {

    private String authority;
    private ApnsSigningKey signingKey;
    private Duration tokenExpiration;

    ApnsChannelManagementHandlerBuilder authority(final String authority) {
      this.authority = authority;
      return this;
    }

    String authority() {
      return this.authority;
    }

    @Override
    public ApnsChannelManagementHandlerBuilder frameLogger(final Http2FrameLogger frameLogger) {
      return super.frameLogger(frameLogger);
    }

    @Override
    public Http2FrameLogger frameLogger() {
      return super.frameLogger();
    }

    public ApnsChannelManagementHandlerBuilder signingKey(final ApnsSigningKey signingKey) {
      this.signingKey = signingKey;
      return this;
    }

    public ApnsSigningKey signingKey() {
      return this.signingKey;
    }

    public ApnsChannelManagementHandlerBuilder tokenExpiration(final Duration tokenExpiration) {
      this.tokenExpiration = tokenExpiration;
      return this;
    }

    public Duration tokenExpiration() {
      return this.tokenExpiration;
    }

    @Override
    protected final boolean isServer() {
      return false;
    }

    @Override
    protected boolean encoderEnforceMaxConcurrentStreams() {
      return true;
    }

    @Override
    public ApnsChannelManagementHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
      Objects.requireNonNull(this.authority(), "Authority must be set before building an ApnsChannelManagementHandler.");

      final ApnsChannelManagementHandler handler = new ApnsChannelManagementHandler(decoder, encoder, initialSettings, this.authority(), this.signingKey(), this.tokenExpiration());
      this.frameListener(handler);
      return handler;
    }

    @Override
    public ApnsChannelManagementHandler build() {
      return super.build();
    }
  }

  ApnsChannelManagementHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings, final String authority, final ApnsSigningKey signingKey, final Duration tokenExpiration) {
    super(decoder, encoder, initialSettings);

    this.authority = authority;
    this.signingKey = signingKey;
    this.tokenExpiration = tokenExpiration;

    this.responseHeadersPropertyKey = this.connection().newKey();
    this.responseDataPropertyKey = this.connection().newKey();
    this.requestPropertyKey = this.connection().newKey();
    this.streamErrorCausePropertyKey = this.connection().newKey();

    this.connection().addListener(this);
  }

  @Override
  public void write(final ChannelHandlerContext context, final Object message, final ChannelPromise writePromise) {
    if (message instanceof ApnsChannelManagementRequest) {
      final ApnsChannelManagementRequest channelManagementRequest = (ApnsChannelManagementRequest) message;

      writePromise.addListener(future -> {
        if (!future.isSuccess()) {
          log.trace("Failed to write push notification.", future.cause());
          channelManagementRequest.getResponseFuture().completeExceptionally(future.cause());
        }
      });

      this.writeRequest(context, channelManagementRequest, writePromise);
    } else {
      // This should never happen, but in case some foreign debris winds up in the pipeline, just pass it through.
      log.error("Unexpected object in pipeline: {}", message);
      context.write(message, writePromise);
    }
  }

  private void retryRequestFromStream(final ChannelHandlerContext context, final int streamId) {
    final Http2Stream stream = this.connection().stream(streamId);

    final ApnsChannelManagementRequest request = stream.removeProperty(this.requestPropertyKey);

    final ChannelPromise writePromise = context.channel().newPromise();
    this.writeRequest(context, request, writePromise);
  }

  private void writeRequest(final ChannelHandlerContext context, final ApnsChannelManagementRequest request, final ChannelPromise writePromise) {
    if (context.channel().isActive()) {
      final int streamId = this.connection().local().incrementAndGetNextStreamId();

      if (streamId > 0) {
        // We'll attach the request and response promise to the stream as soon as the stream is created.
        // Because we're using a StreamBufferingEncoder under the hood, there's no guarantee as to when the stream
        // will actually be created, and so we attach these in the onStreamAdded listener to make sure everything
        // is happening in a predictable order.
        this.unattachedRequestsByStreamId.put(streamId, request);

        final Http2Headers headers = getHeadersForRequest(request, context, streamId);

        final ChannelPromise headersPromise = context.newPromise();
        this.encoder().writeHeaders(context, streamId, headers, 0, false, headersPromise);
        log.trace("Wrote headers on stream {}: {}", streamId, headers);

        final ByteBuf payloadBuffer =
            Unpooled.wrappedBuffer(request.getPayload().getBytes(StandardCharsets.UTF_8));

        final ChannelPromise dataPromise = context.newPromise();
        this.encoder().writeData(context, streamId, payloadBuffer, 0, true, dataPromise);
        log.trace("Wrote payload on stream {}: {}", streamId, request.getPayload());

        final PromiseCombiner promiseCombiner = new PromiseCombiner(context.executor());
        promiseCombiner.addAll((ChannelFuture) headersPromise, dataPromise);
        promiseCombiner.finish(writePromise);
      } else {
        // This is very unlikely, but in the event that we run out of stream IDs, we need to open a new
        // connection. Just closing the context should be enough; automatic reconnection should take things
        // from there.
        writePromise.tryFailure(STREAMS_EXHAUSTED_EXCEPTION);
        context.channel().close();
      }
    } else {
      writePromise.tryFailure(STREAM_CLOSED_BEFORE_REPLY_EXCEPTION);
    }
  }

  protected Http2Headers getHeadersForRequest(final ApnsChannelManagementRequest request, final ChannelHandlerContext context, final int streamId) {
    final Http2Headers headers = new DefaultHttp2Headers()
        .scheme(HttpScheme.HTTPS.name())
        .authority(this.authority)
        .method(request.getMethod().asciiName())
        .path(request.getPath());

    if (request.getApnsRequestId() != null) {
      headers.add(APNS_REQUEST_ID_HEADER, request.getApnsRequestId().toString());
    }

    if (this.authenticationToken == null) {
      log.debug("Generated a new authentication token for channel {} at stream {}", context.channel(), streamId);
      this.authenticationToken = new AuthenticationToken(this.signingKey, Instant.now());

      tokenExpirationFuture = context.executor().schedule(() -> {
        log.debug("Authentication token for channel {} has expired", context.channel());
        this.authenticationToken = null;
      }, this.tokenExpiration.toMillis(), TimeUnit.MILLISECONDS);

      headers.add(APNS_AUTHORIZATION_HEADER, this.authenticationToken.getAuthorizationHeader());
    }

    return headers.add(request.getHeaders());
  }

  @Override
  public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) {
    final int bytesProcessed = data.readableBytes() + padding;

    final Http2Stream stream = this.connection().stream(streamId);

    // TODO Retain?
    ((CompositeByteBuf) stream.getProperty(this.responseDataPropertyKey)).addComponent(data);

    if (endOfStream) {
      this.handleEndOfStream(stream);
    }

    return bytesProcessed;
  }

  @Override
  public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) {
    this.onHeadersRead(context, streamId, headers, padding, endOfStream);
  }

  @Override
  public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) {
    final Http2Stream stream = this.connection().stream(streamId);
    stream.setProperty(this.responseHeadersPropertyKey, headers);

    if (endOfStream) {
      this.handleEndOfStream(stream);
    }
  }

  private void handleEndOfStream(final Http2Stream stream) {

    final ApnsChannelManagementRequest request = stream.getProperty(this.requestPropertyKey);

    request.getResponseFuture().complete(new Http2Response(stream.getProperty(this.responseHeadersPropertyKey),
        ByteBufUtil.getBytes(stream.getProperty(this.responseDataPropertyKey))));
  }

  @Override
  public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency, final short weight, final boolean exclusive) {
  }

  @Override
  public void onRstStreamRead(final ChannelHandlerContext context, final int streamId, final long errorCode) {
    if (errorCode == Http2Error.REFUSED_STREAM.code()) {
      // This can happen if the server reduces MAX_CONCURRENT_STREAMS while we already have notifications in
      // flight. We may get multiple RST_STREAM frames per stream since we send multiple frames (HEADERS and
      // DATA) for each push notification, but we should only get one REFUSED_STREAM error; the rest should all be
      // STREAM_CLOSED.
      this.retryRequestFromStream(context, streamId);
    }
  }

  @Override
  public void onSettingsAckRead(final ChannelHandlerContext ctx) {
  }

  @Override
  public void onSettingsRead(final ChannelHandlerContext context, final Http2Settings settings) {
    log.debug("Received settings from APNs gateway: {}", settings);

    // Always try to mark the "channel ready" promise as a success after we receive a SETTINGS frame. If it's the
    // first SETTINGS frame, we know all handshaking and connection setup is done and the channel is ready to use.
    // If it's a subsequent SETTINGS frame, this will have no effect.
    getChannelReadyPromise(context.channel()).trySuccess(context.channel());
  }

  @Override
  public void onPingRead(final ChannelHandlerContext ctx, final long pingData) {
  }

  @Override
  public void onPingAckRead(final ChannelHandlerContext context, final long pingData) {
  }

  @Override
  public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) {
  }

  @Override
  public void onGoAwayRead(final ChannelHandlerContext context, final int lastStreamId, final long errorCode, final ByteBuf debugData) {
    log.info("Received GOAWAY from APNs channel management server: {}", debugData.toString(StandardCharsets.UTF_8));
    context.close();
  }

  @Override
  public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement) {
  }

  @Override
  public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) {
  }

  @Override
  public void onStreamAdded(final Http2Stream stream) {
    stream.setProperty(this.requestPropertyKey, this.unattachedRequestsByStreamId.remove(stream.id()));
    stream.setProperty(this.responseDataPropertyKey, Unpooled.compositeBuffer());
  }

  @Override
  public void onStreamActive(final Http2Stream stream) {
  }

  @Override
  public void onStreamHalfClosed(final Http2Stream stream) {
  }

  @Override
  public void onStreamClosed(final Http2Stream stream) {
    // Always try to fail promises associated with closed streams; most of the time, this should fail silently, but
    // in cases of unexpected closure, it will make sure that nothing gets left hanging.
    final ApnsChannelManagementRequest request = stream.getProperty(this.requestPropertyKey);

    if (request != null) {
      final Throwable cause;

      if (stream.getProperty(this.streamErrorCausePropertyKey) != null) {
        cause = stream.getProperty(this.streamErrorCausePropertyKey);
      } else if (this.connectionErrorCause != null) {
        cause = this.connectionErrorCause;
      } else {
        cause = STREAM_CLOSED_BEFORE_REPLY_EXCEPTION;
      }

      request.getResponseFuture().completeExceptionally(cause);
    }
  }

  @Override
  public void onStreamRemoved(final Http2Stream stream) {
    stream.removeProperty(this.responseHeadersPropertyKey);
    stream.removeProperty(this.requestPropertyKey);

    final CompositeByteBuf responseData = stream.removeProperty(this.responseDataPropertyKey);
    responseData.release();
  }

  @Override
  public void onGoAwaySent(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
  }

  @Override
  public void onGoAwayReceived(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
  }

  @Override
  protected void onStreamError(final ChannelHandlerContext context, final boolean isOutbound, final Throwable cause, final Http2Exception.StreamException streamException) {
    final Http2Stream stream = this.connection().stream(streamException.streamId());

    // The affected stream may already be closed (or was never open in the first place)
    if (stream != null) {
      stream.setProperty(this.streamErrorCausePropertyKey, streamException);
    }

    super.onStreamError(context, isOutbound, cause, streamException);
  }

  @Override
  protected void onConnectionError(final ChannelHandlerContext context, final boolean isOutbound, final Throwable cause, final Http2Exception http2Exception) {
    this.connectionErrorCause = http2Exception != null ? http2Exception : cause;

    super.onConnectionError(context, isOutbound, cause, http2Exception);
  }

  @Override
  public void channelInactive(final ChannelHandlerContext context) throws Exception {
    for (final ApnsChannelManagementRequest request : this.unattachedRequestsByStreamId.values()) {
      request.getResponseFuture().completeExceptionally(STREAM_CLOSED_BEFORE_REPLY_EXCEPTION);
    }

    this.unattachedRequestsByStreamId.clear();

    if (getChannelReadyPromise(context.channel()).tryFailure(STREAM_CLOSED_BEFORE_REPLY_EXCEPTION)) {
      log.debug("Channel became inactive before SETTINGS frame received");
    }

    if (this.tokenExpirationFuture != null) {
      this.tokenExpirationFuture.cancel(false);
      this.tokenExpirationFuture = null;
    }

    super.channelInactive(context);
  }

  public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
    // Always try to fail the "channel ready" promise if we catch an exception; in some cases, these may happen
    // after a connection has already become ready, in which case the failure attempt will have no effect.
    getChannelReadyPromise(context.channel()).tryFailure(cause);
  }

  private Promise<Channel> getChannelReadyPromise(final Channel channel) {
    return channel.attr(AbstractApnsChannelFactory.CHANNEL_READY_PROMISE_ATTRIBUTE_KEY).get();
  }
}
