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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.relayrides.pushy.apns.ApnsConnection.ApnsFrameItem;
import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public class MockApnsServer {

	private final int port;
	private final NioEventLoopGroup eventLoopGroup;

	private final ArrayList<CountDownLatch> countdownLatches = new ArrayList<CountDownLatch>();

	private Channel channel;

	private boolean shouldSendErrorResponses = true;
	private boolean shouldSendIncorrectSequenceNumber = false;

	public static final int EXPECTED_TOKEN_SIZE = 32;
	public static final int MAX_PAYLOAD_SIZE = 2048;

	private static final Logger log = LoggerFactory.getLogger(MockApnsServer.class);

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
		FRAME_LENGTH,
		FRAME;
	}

	private class ApnsPushNotificationDecoder extends ReplayingDecoder<ApnsPushNotificationDecoderState> {

		private int sequenceNumber;
		private Date deliveryInvalidation;
		private byte[] token;
		private byte[] payloadBytes;
		private DeliveryPriority priority;

		private byte[] frame;

		private boolean hasReceivedDeliveryInvalidationTime;
		private boolean hasReceivedSequenceNumber;

		private static final byte BINARY_NOTIFICATION_OPCODE = 2;

		public ApnsPushNotificationDecoder() {
			super(ApnsPushNotificationDecoderState.OPCODE);
		}

		@Override
		protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) throws ApnsDecoderException {
			switch (this.state()) {
				case OPCODE: {

					this.sequenceNumber = 0;
					this.deliveryInvalidation = null;
					this.token = null;
					this.payloadBytes = null;
					this.priority = null;
					this.frame = null;

					this.hasReceivedDeliveryInvalidationTime = false;
					this.hasReceivedSequenceNumber = false;

					final byte opcode = in.readByte();

					if (opcode == BINARY_NOTIFICATION_OPCODE) {
						this.checkpoint(ApnsPushNotificationDecoderState.FRAME_LENGTH);
					} else {
						throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
					}

					break;
				}

				case FRAME_LENGTH: {
					final int frameSize = in.readInt();

					if (frameSize < 1) {
						throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
					}

					this.frame = new byte[frameSize];
					this.checkpoint(ApnsPushNotificationDecoderState.FRAME);

					break;
				}

				case FRAME: {
					in.readBytes(this.frame);

					out.add(this.decodeNotificationFromFrame(this.frame));
					this.checkpoint(ApnsPushNotificationDecoderState.OPCODE);

					break;
				}
			}
		}

		private SendableApnsPushNotification<SimpleApnsPushNotification> decodeNotificationFromFrame(final byte[] frame) throws ApnsDecoderException {
			final ByteBuffer buffer = ByteBuffer.wrap(frame);

			while (buffer.hasRemaining()) {
				try {
					final ApnsFrameItem item = ApnsFrameItem.getFrameItemFromCode(buffer.get());
					final short itemLength = buffer.getShort();

					switch (item) {
						case DEVICE_TOKEN: {
							if (this.token != null) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
							}

							this.token = new byte[itemLength];

							if (this.token.length == 0) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.MISSING_TOKEN);
							} else if (this.token.length != EXPECTED_TOKEN_SIZE) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.INVALID_TOKEN_SIZE);
							}

							buffer.get(this.token);

							break;
						}

						case DELIVERY_INVALIDATION_TIME: {
							if (this.hasReceivedDeliveryInvalidationTime) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
							}

							final long timestamp = (buffer.getInt() & 0xFFFFFFFFL) * 1000L;
							this.deliveryInvalidation = timestamp > 0 ? new Date(timestamp) : null;

							this.hasReceivedDeliveryInvalidationTime = true;

							break;
						}

						case PAYLOAD: {
							if (this.payloadBytes != null) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
							}

							this.payloadBytes = new byte[itemLength];

							if (this.payloadBytes.length == 0) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.MISSING_PAYLOAD);
							} else if (this.payloadBytes.length > MAX_PAYLOAD_SIZE) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.INVALID_PAYLOAD_SIZE);
							}

							buffer.get(this.payloadBytes);

							break;
						}

						case PRIORITY: {
							if (this.priority != null) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
							}

							this.priority = DeliveryPriority.getFromCode(buffer.get());

							break;
						}

						case SEQUENCE_NUMBER: {
							if (this.hasReceivedSequenceNumber) {
								throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
							}

							this.sequenceNumber = buffer.getInt();
							this.hasReceivedSequenceNumber = true;

							break;
						}
					}
				} catch (final RuntimeException e) {
					throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
				}
			}

			return this.constructPushNotification();
		}

		private SendableApnsPushNotification<SimpleApnsPushNotification> constructPushNotification() throws ApnsDecoderException {
			if (!this.hasReceivedSequenceNumber || !this.hasReceivedDeliveryInvalidationTime || this.token == null || this.payloadBytes == null || this.priority == null) {
				throw new ApnsDecoderException(this.sequenceNumber, RejectedNotificationReason.UNKNOWN);
			}

			final String payloadString = new String(this.payloadBytes, Charset.forName("UTF-8"));

			return new SendableApnsPushNotification<SimpleApnsPushNotification>(
					new SimpleApnsPushNotification(this.token, payloadString, this.deliveryInvalidation, this.priority),
					this.sequenceNumber);
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
					if (this.server.shouldSendErrorResponses()) {
						final ApnsDecoderException apnsDecoderException = (ApnsDecoderException)decoderException.getCause();
						final int sequenceNumber = this.server.shouldSendIncorrectSequenceNumber ? 0 : apnsDecoderException.sequenceNumber;

						final RejectedNotification rejectedNotification =
								new RejectedNotification(sequenceNumber, apnsDecoderException.reason);

						context.writeAndFlush(rejectedNotification).addListener(ChannelFutureListener.CLOSE);
					}
				}
			} else {
				log.warn("Caught an unexpected exception; closing connection.", cause);
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
	}

	public void setShouldSendErrorResponses(final boolean shouldSendErrorResponses) {
		this.shouldSendErrorResponses = shouldSendErrorResponses;
	}

	public boolean shouldSendErrorResponses() {
		return this.shouldSendErrorResponses;
	}

	public boolean shouldSendIncorrectSequenceNumber() {
		return shouldSendIncorrectSequenceNumber;
	}

	public void setShouldSendIncorrectSequenceNumber(boolean shouldSendIncorrectSequenceNumber) {
		this.shouldSendIncorrectSequenceNumber = shouldSendIncorrectSequenceNumber;
	}

	private void acceptNotification(final SendableApnsPushNotification<SimpleApnsPushNotification> receivedNotification) {
		synchronized (this.countdownLatches) {
			for (final CountDownLatch latch : this.countdownLatches) {
				latch.countDown();
			}
		}
	}

	public CountDownLatch getAcceptedNotificationCountDownLatch(final int acceptedNotificationCount) {
		synchronized (this.countdownLatches) {
			final CountDownLatch latch = new CountDownLatch(acceptedNotificationCount);
			this.countdownLatches.add(latch);

			return latch;
		}
	}
}
