package com.relayrides.pushy.apns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;

class ApnsClientHandler extends Http2ConnectionHandler implements Http2FrameListener {

    public static final class Builder extends BuilderBase<ApnsClientHandler, Builder> {
        @Override
        public ApnsClientHandler build0(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder) {
            final ApnsClientHandler handler = new ApnsClientHandler(decoder, encoder, initialSettings());
            this.frameListener(handler);
            return handler;
        }
    }

    protected ApnsClientHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder,
            final Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data, final int padding,
            final boolean endOfStream) throws Http2Exception {
        return data.readableBytes() + padding;
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endOfStream) throws Http2Exception {
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        this.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
            boolean exclusive) throws Http2Exception {
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
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId, Http2Headers headers,
            int padding) throws Http2Exception {
    }

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception {
    }

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception {
    }

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
            ByteBuf payload) throws Http2Exception {
    }
}
