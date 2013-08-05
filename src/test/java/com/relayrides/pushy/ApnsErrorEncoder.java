package com.relayrides.pushy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ApnsErrorEncoder extends MessageToByteEncoder<ApnsException> {

	private static final byte ERROR_COMMAND = 8;
	
	@Override
	protected void encode(final ChannelHandlerContext context, final ApnsException e, final ByteBuf out) {
		out.writeByte(ERROR_COMMAND);
		out.writeByte(e.getErrorCode().getCode());
		out.writeInt(e.getNotificationId());
	}

}
