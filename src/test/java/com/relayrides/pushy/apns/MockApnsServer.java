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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.ssl.SslHandler;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class MockApnsServer {

	private final int port;
	private final NioEventLoopGroup eventLoopGroup;

	private final Vector<CountDownLatch> countdownLatches = new Vector<CountDownLatch>();

	private Channel channel;

	public static final int EXPECTED_TOKEN_SIZE = 32;
	public static final int MAX_PAYLOAD_SIZE = 256;

	private class ApnsDecoderException extends Exception {
		private static final long serialVersionUID = 1L;

		final int sequenceNumber;
		final RejectedNotificationReason reason;

		public ApnsDecoderException(final int sequenceNumber, final RejectedNotificationReason reason) {
			this.sequenceNumber = sequenceNumber;
			this.reason = reason;
		}
	};

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
		protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) throws ApnsDecoderException {
			switch (this.state()) {
				case OPCODE: {
					final byte opcode = in.readByte();

					if (opcode != EXPECTED_OPCODE) {
						throw new ApnsDecoderException(0, RejectedNotificationReason.UNKNOWN);
					}

					this.checkpoint(ApnsPushNotificationDecoderState.SEQUENCE_NUMBER);

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
						throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.MISSING_TOKEN);
					} else if (this.token.length != EXPECTED_TOKEN_SIZE) {
						throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.INVALID_TOKEN_SIZE);
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

					if (payloadSize == 0) {
						throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.MISSING_PAYLOAD);
					} else if (payloadSize > MAX_PAYLOAD_SIZE) {
						throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.INVALID_PAYLOAD_SIZE);
					}

					this.payloadBytes = new byte[payloadSize];
					this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD);

					break;
				}

				case PAYLOAD: {
					in.readBytes(this.payloadBytes);

					final String payloadString = new String(this.payloadBytes, Charset.forName("UTF-8"));

					out.add(new SendableApnsPushNotification<SimpleApnsPushNotification>(
							new SimpleApnsPushNotification(this.token, payloadString, this.expiration),
							this.sequenceNumber));

					this.checkpoint(ApnsPushNotificationDecoderState.OPCODE);

					break;
				}
			}
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

		private boolean rejectFutureNotifications = false;

		public MockApnsServerHandler(final MockApnsServer server) {
			this.server = server;
		}

		@Override
		protected void channelRead0(final ChannelHandlerContext context, final SendableApnsPushNotification<SimpleApnsPushNotification> receivedNotification) {
			if (!this.rejectFutureNotifications) {
				this.server.acceptNotification(receivedNotification);
			}
		}

		@Override
		public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
			this.rejectFutureNotifications = true;

			if (cause instanceof DecoderException) {
				final DecoderException decoderException = (DecoderException)cause;

				if (decoderException.getCause() instanceof ApnsDecoderException) {
					final ApnsDecoderException apnsDecoderException = (ApnsDecoderException)decoderException.getCause();
					final RejectedNotification rejectedNotification =
							new RejectedNotification(apnsDecoderException.sequenceNumber, apnsDecoderException.reason);

					context.writeAndFlush(rejectedNotification).addListener(ChannelFutureListener.CLOSE);
				}
			} else {
				context.close();
			}
		}
	}

	public MockApnsServer(final int port, final NioEventLoopGroup eventLoopGroup) {
		this.port = port;
		this.eventLoopGroup = eventLoopGroup;

	}

	public synchronized void start() throws InterruptedException {
		final ServerBootstrap bootstrap = new ServerBootstrap();

		bootstrap.group(this.eventLoopGroup);
		bootstrap.channel(NioServerSocketChannel.class);
		bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

		final MockApnsServer server = this;

		bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				channel.pipeline().addLast("ssl", new SslHandler(SSLTestUtil.createSSLEngineForMockServer()));
				channel.pipeline().addLast("encoder", new ApnsErrorEncoder());
				channel.pipeline().addLast("decoder", new ApnsPushNotificationDecoder());
				channel.pipeline().addLast("handler", new MockApnsServerHandler(server));
			}
		});

		this.channel = bootstrap.bind(this.port).await().channel();
	}

	public synchronized void shutdown() throws InterruptedException {
		if (this.channel != null) {
			this.channel.close().await();
		}

		this.channel = null;
		this.countdownLatches.clear();
	}

	protected void acceptNotification(final SendableApnsPushNotification<SimpleApnsPushNotification> receivedNotification) {
		for (final CountDownLatch latch : this.countdownLatches) {
			latch.countDown();
		}
	}

	public CountDownLatch getAcceptedNotificationCountDownLatch(final int acceptedNotificationCount) {
		final CountDownLatch latch = new CountDownLatch(acceptedNotificationCount);
		this.countdownLatches.add(latch);

		return latch;
	}
}
