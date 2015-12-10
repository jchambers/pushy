package com.relayrides.pushy.apns;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;

class MockApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

	private MockApnsServer server;

	private Map<Integer, UUID> requestsWaitingForDataFrame = new HashMap<Integer, UUID>();

	private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
			.status(HttpResponseStatus.OK.codeAsText());

	private static final String APNS_ID = "apns-id";
	private static final String APNS_EXPIRATION = "apns-expiration";

	private static final int MAX_CONTENT_LENGTH = 4096;

	private static final String PATH_PREFIX = "/3/device/";

	public static final class Builder extends BuilderBase<MockApnsServerHandler, Builder> {
		@Override
		public MockApnsServerHandler build0(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder) {
			final MockApnsServerHandler handler = new MockApnsServerHandler(decoder, encoder, initialSettings());
			this.frameListener(handler);
			return handler;
		}
	}

	protected MockApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
		super(decoder, encoder, initialSettings);
	}

	@Override
	public int onDataRead(final ChannelHandlerContext context, final int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
		final int bytesProcessed = data.readableBytes() + padding;

		if (endOfStream) {
			// Presumably, we replied as soon as we got the headers if we don't have a UUID associated with this stream
			if (this.requestsWaitingForDataFrame.containsKey(streamId)) {
				// TODO Are we actually supposed to use this ID in the response?
				final UUID apnsId = this.requestsWaitingForDataFrame.remove(streamId);
				this.sendSuccessResponse(context, streamId);
			}
		}

		return bytesProcessed;
	}

	@Override
	public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
		if (!HttpMethod.POST.asciiName().contentEquals(headers.get(Http2Headers.PseudoHeaderName.METHOD.value()))) {
			// TODO Respond with HTTP/405
		}

		if (endOfStream) {
			// TODO Respond with HTTP/400 (since we don't expect a payload to follow)
		}

		{
			final Integer contentLength = headers.getInt(HttpHeaderNames.CONTENT_LENGTH);

			if (contentLength != null && contentLength > MAX_CONTENT_LENGTH) {
				// TODO Respond with HTTP/400 (PayloadTooLarge)
			} else if (contentLength == null) {
				// TODO Respond with HTTP/400
			}
		}

		{
			final CharSequence pathSequence = headers.get(Http2Headers.PseudoHeaderName.PATH.value());

			if (pathSequence != null) {
				final String pathString = pathSequence.toString();

				if (pathString.startsWith(PATH_PREFIX)) {
					final String tokenString = pathString.substring(PATH_PREFIX.length());

					// TODO Do token validation things
				}
			} else {
				// TODO Respond with HTTP/404
			}
		}

		{
			final CharSequence apnsIdSequence = headers.get(APNS_ID);

			final UUID apnsId;

			if (apnsIdSequence != null) {
				// TODO Handle IllegalArgumentException here
				apnsId = UUID.fromString(apnsIdSequence.toString());
			} else {
				// If the client didn't send us a UUID, make one up (for now)
				apnsId = UUID.randomUUID();
			}

			this.requestsWaitingForDataFrame.put(streamId, apnsId);
		}
	}

	@Override
	public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
			short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {

		this.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
	}

	@Override
	public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight, boolean exclusive) throws Http2Exception {
	}

	@Override
	public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
	}

	@Override
	public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
	}

	@Override
	public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
	}

	@Override
	public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
	}

	@Override
	public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
	}

	@Override
	public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
	}

	@Override
	public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) throws Http2Exception {
	}

	@Override
	public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) throws Http2Exception {
	}

	@Override
	public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload) throws Http2Exception {
	}

	private void sendSuccessResponse(final ChannelHandlerContext context, final int streamId) {
		this.encoder().writeHeaders(context, streamId, SUCCESS_HEADERS, 0, true, context.newPromise());
		context.flush();
	}

	private void sendErrorResponse(final ChannelHandlerContext context, final int streamId, final HttpResponseStatus responseStatus, final Date timestamp) {
		// TODO
		/* this.encoder().writeHeaders(context, streamId, headers, 0, false, context.newPromise());
        this.encoder().writeData(context, streamId, data, 0, true, context.newPromise());
        context.flush(); */
	}
}
