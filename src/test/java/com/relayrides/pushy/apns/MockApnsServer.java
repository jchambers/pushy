/* Copyright (c) 2013 RelayRides
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

package com.relayrides.pushy.apns;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.GenericFutureListener;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLEngine;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class MockApnsServer {

	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;

	private final int port;

	private final Vector<SimpleApnsPushNotification> receivedNotifications;
	private final Vector<CountDownLatch> countdownLatches;

	private int failWithErrorCount = -1;
	private RejectedNotificationReason errorCode;

	public static final int MAX_PAYLOAD_SIZE = 256;

	private enum ApnsPushNotificationDecoderState {
		OPCODE,
		SEQUENCE_NUMBER,
		EXPIRATION,
		TOKEN_LENGTH,
		TOKEN,
		PAYLOAD_LENGTH,
		PAYLOAD
	}

	private class ApnsPushNotificationDecoder extends ReplayingDecoder<ApnsPushNotificationDecoderState> {

		private int sequenceNumber;
		private Date expiration;
		private byte[] token;
		private byte[] payloadBytes;

		private static final byte EXPECTED_OPCODE = 1;

		public ApnsPushNotificationDecoder() {
			super(ApnsPushNotificationDecoderState.OPCODE);
		}

		@Override
		protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
			switch (this.state()) {
				case OPCODE: {
					final byte opcode = in.readByte();

					if (opcode != EXPECTED_OPCODE) {
						reportErrorAndCloseConnection(context, 0, RejectedNotificationReason.UNKNOWN);
					} else {
						this.checkpoint(ApnsPushNotificationDecoderState.SEQUENCE_NUMBER);
					}

					break;
				}

				case SEQUENCE_NUMBER: {
					this.sequenceNumber = in.readInt();
					this.checkpoint(ApnsPushNotificationDecoderState.EXPIRATION);

					break;
				}

				case EXPIRATION: {
					final long timestamp = (in.readInt() & 0xFFFFFFFFL) * 1000L;
					this.expiration = new Date(timestamp);

					this.checkpoint(ApnsPushNotificationDecoderState.TOKEN_LENGTH);

					break;
				}

				case TOKEN_LENGTH: {

					this.token = new byte[in.readShort() & 0x0000FFFF];

					if (this.token.length == 0) {
						this.reportErrorAndCloseConnection(context, this.sequenceNumber, RejectedNotificationReason.MISSING_TOKEN);
					}

					this.checkpoint(ApnsPushNotificationDecoderState.TOKEN);

					break;
				}

				case TOKEN: {
					in.readBytes(this.token);
					this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD_LENGTH);

					break;
				}

				case PAYLOAD_LENGTH: {
					final int payloadSize = in.readShort() & 0x0000FFFF;

					if (payloadSize > MAX_PAYLOAD_SIZE || payloadSize == 0) {
						this.reportErrorAndCloseConnection(context, this.sequenceNumber, RejectedNotificationReason.INVALID_PAYLOAD_SIZE);
					} else {
						this.payloadBytes = new byte[payloadSize];
						this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD);
					}

					break;
				}

				case PAYLOAD: {
					in.readBytes(this.payloadBytes);

					final String payloadString = new String(this.payloadBytes, Charset.forName("UTF-8"));

					final SimpleApnsPushNotification pushNotification =
							new SimpleApnsPushNotification(this.token, payloadString, this.expiration);

					out.add(new SendableApnsPushNotification<SimpleApnsPushNotification>(pushNotification, this.sequenceNumber));
					this.checkpoint(ApnsPushNotificationDecoderState.OPCODE);

					break;
				}
			}
		}

		private void reportErrorAndCloseConnection(final ChannelHandlerContext context, final int notificationId, final RejectedNotificationReason errorCode) {
			context.writeAndFlush(new RejectedNotification(notificationId, errorCode)).addListener(new GenericFutureListener<ChannelFuture>() {

				public void operationComplete(ChannelFuture future) {
					context.close();
				}
			});
		}
	}

	private class ApnsErrorEncoder extends MessageToByteEncoder<RejectedNotification> {

		private static final byte ERROR_COMMAND = 8;

		@Override
		protected void encode(final ChannelHandlerContext context, final RejectedNotification rejectedNotification, final ByteBuf out) {
			out.writeByte(ERROR_COMMAND);
			out.writeByte(rejectedNotification.getReason().getErrorCode());
			out.writeInt(rejectedNotification.getSequenceNumber());
		}
	}

	private class MockApnsServerHandler extends SimpleChannelInboundHandler<SendableApnsPushNotification<SimpleApnsPushNotification>> {

		private final MockApnsServer server;

		private boolean rejectFutureMessages = false;

		public MockApnsServerHandler(final MockApnsServer server) {
			this.server = server;
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, SendableApnsPushNotification<SimpleApnsPushNotification> receivedNotification) throws Exception {

			if (!this.rejectFutureMessages) {
				final RejectedNotification rejection = this.server.handleReceivedNotification(receivedNotification);

				if (rejection != null) {

					this.rejectFutureMessages = true;

					context.writeAndFlush(rejection).addListener(new GenericFutureListener<ChannelFuture>() {

						public void operationComplete(final ChannelFuture future) {
							context.close();
						}

					});
				}
			}
		}
	}

	public MockApnsServer(final int port) {
		this.port = port;

		this.receivedNotifications = new Vector<SimpleApnsPushNotification>();
		this.countdownLatches = new Vector<CountDownLatch>();
	}

	public void start() throws InterruptedException {
		this.bossGroup = new NioEventLoopGroup();
		this.workerGroup = new NioEventLoopGroup();

		final ServerBootstrap bootstrap = new ServerBootstrap();

		bootstrap.group(this.bossGroup, this.workerGroup);
		bootstrap.channel(NioServerSocketChannel.class);

		final SSLEngine sslEngine;

		try {
			sslEngine = SSLUtil.createMockServerSSLEngine();
		} catch (Exception e) {
			throw new RuntimeException("Failed to create SSL engine for mock server.", e);
		}

		final MockApnsServer server = this;

		bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				channel.pipeline().addLast("ssl", new SslHandler(sslEngine));
				channel.pipeline().addLast("encoder", new ApnsErrorEncoder());
				channel.pipeline().addLast("decoder", new ApnsPushNotificationDecoder());
				channel.pipeline().addLast("handler", new MockApnsServerHandler(server));
			}

		});

		bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

		bootstrap.bind(this.port).sync();
	}

	public void shutdown() throws InterruptedException {
		this.workerGroup.shutdownGracefully();
		this.bossGroup.shutdownGracefully();
	}

	public void failWithErrorAfterNotifications(final RejectedNotificationReason errorCode, final int notificationCount) {
		this.failWithErrorCount = notificationCount;
		this.errorCode = errorCode;
	}

	protected RejectedNotification handleReceivedNotification(final SendableApnsPushNotification<SimpleApnsPushNotification> receivedNotification) {

		this.receivedNotifications.add(receivedNotification.getPushNotification());
		final int notificationCount = this.receivedNotifications.size();

		for (final CountDownLatch latch : this.countdownLatches) {
			latch.countDown();
		}

		if (notificationCount == this.failWithErrorCount) {
			return new RejectedNotification(receivedNotification.getSequenceNumber(), this.errorCode);
		} else {
			return null;
		}
	}

	public List<SimpleApnsPushNotification> getReceivedNotifications() {
		return new ArrayList<SimpleApnsPushNotification>(this.receivedNotifications);
	}

	public CountDownLatch getCountDownLatch(final int notificationCount) {
		final CountDownLatch latch = new CountDownLatch(notificationCount);
		this.countdownLatches.add(latch);

		return latch;
	}
}
