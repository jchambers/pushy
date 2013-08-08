package com.relayrides.pushy.apns;

import com.relayrides.pushy.apns.RejectedNotificationException;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ApnsErrorEncoder extends MessageToByteEncoder<RejectedNotificationException> {

	private static final byte ERROR_COMMAND = 8;
	
	@Override
	protected void encode(final ChannelHandlerContext context, final RejectedNotificationException e, final ByteBuf out) {
		out.writeByte(ERROR_COMMAND);
		out.writeByte(e.getErrorCode().getCode());
		out.writeInt(e.getSequenceNumberId());
	}

}
