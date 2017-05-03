package com.relayrides.pushy.apns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;

class BenchmarkApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText());

    public static final class BenchmarkApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<BenchmarkApnsServerHandler, BenchmarkApnsServerHandlerBuilder> {
        @Override
        public BenchmarkApnsServerHandlerBuilder initialSettings(final Http2Settings initialSettings) {
            return super.initialSettings(initialSettings);
        }

        @Override
        public BenchmarkApnsServerHandler build(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
            final BenchmarkApnsServerHandler handler = new BenchmarkApnsServerHandler(decoder, encoder, initialSettings);
            this.frameListener(handler);
            return handler;
        }

        @Override
        public BenchmarkApnsServerHandler build() {
            return super.build();
        }
    }

    protected BenchmarkApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
        if (endOfStream) {
            this.encoder().writeHeaders(context, streamId, SUCCESS_HEADERS, 0, true, context.channel().newPromise());
        }

        return data.readableBytes() + padding;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {
    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency, final short weight, final boolean exclusive) throws Http2Exception {
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext ctx, final int streamId, final long errorCode) throws Http2Exception {
    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext ctx) throws Http2Exception {
    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext ctx, final Http2Settings settings) throws Http2Exception {
    }

    @Override
    public void onPingRead(final ChannelHandlerContext ctx, final ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPingAckRead(final ChannelHandlerContext ctx, final ByteBuf data) throws Http2Exception {
    }

    @Override
    public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(final ChannelHandlerContext ctx, final int lastStreamId, final long errorCode, final ByteBuf debugData) throws Http2Exception {
    }

    @Override
    public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement) throws Http2Exception {
    }

    @Override
    public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) throws Http2Exception {
    }
}
