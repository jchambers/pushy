/*
 * Copyright (c) 2013-2018 Turo
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

package com.turo.pushy.apns.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.*;
import io.netty.util.AsciiString;

import java.util.UUID;

class BenchmarkApnsServerHandler extends Http2ConnectionHandler implements Http2FrameListener {

    private static final Http2Headers SUCCESS_HEADERS = new DefaultHttp2Headers()
            .status(HttpResponseStatus.OK.codeAsText())
            .add(new AsciiString("apns-id"), new AsciiString(UUID.randomUUID().toString()));

    public static class BenchmarkApnsServerHandlerBuilder extends AbstractHttp2ConnectionHandlerBuilder<BenchmarkApnsServerHandler, BenchmarkApnsServerHandlerBuilder> {

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

    BenchmarkApnsServerHandler(final Http2ConnectionDecoder decoder, final Http2ConnectionEncoder encoder, final Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    @Override
    public int onDataRead(final ChannelHandlerContext context, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) {
        if (endOfStream) {
            handleEndOfStream(context, streamId);
        }

        return data.readableBytes() + padding;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) {
        if (endOfStream) {
            handleEndOfStream(context, streamId);
        }
    }

    private void handleEndOfStream(final ChannelHandlerContext context, final int streamId) {
        this.encoder().writeHeaders(context, streamId, SUCCESS_HEADERS, 0, true, context.channel().newPromise());
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext context, final int streamId, final Http2Headers headers, final int streamDependency,
                              final short weight, final boolean exclusive, final int padding, final boolean endOfStream) {

        this.onHeadersRead(context, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext context, final int streamId, final int streamDependency, final short weight, final boolean exclusive) {
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext context, final int streamId, final long errorCode) {
    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext context) {
    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext context, final Http2Settings settings) {
    }

    @Override
    public void onPingRead(final ChannelHandlerContext context, final long data) {
    }

    @Override
    public void onPingAckRead(final ChannelHandlerContext context, final long data) {
    }

    @Override
    public void onPushPromiseRead(final ChannelHandlerContext context, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) {
    }

    @Override
    public void onGoAwayRead(final ChannelHandlerContext context, final int lastStreamId, final long errorCode, final ByteBuf debugData) {
    }

    @Override
    public void onWindowUpdateRead(final ChannelHandlerContext context, final int streamId, final int windowSizeIncrement) {
    }

    @Override
    public void onUnknownFrame(final ChannelHandlerContext context, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) {
    }
}
