package com.relayrides.pushy.apns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class ApnsErrorDecoder extends ByteToMessageDecoder {

	// Per Apple's docs, APNS errors will have a one-byte "command", a one-byte status, and a 4-byte notification ID
	private static final int EXPECTED_BYTES = 6;
	private static final byte EXPECTED_COMMAND = 8;
	
	@Override
	protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
		if (in.readableBytes() >= EXPECTED_BYTES) {
			final byte command = in.getByte(0);
			final byte code = in.getByte(1);
			
			final int notificationId = in.getInt(2);
			
			if (command != EXPECTED_COMMAND) {
				throw new IllegalArgumentException(String.format("Unexpected command: %d", command));
			}
			
			final ApnsErrorCode errorCode = ApnsErrorCode.getByCode(code);
			
			out.add(new ApnsException(notificationId, errorCode));
		}
	}
}
