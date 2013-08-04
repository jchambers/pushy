package com.relayrides.pushy.apns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.Date;

public class PushNotificationEncoder extends MessageToByteEncoder<SendableApnsPushNotification> {

	final byte PUSH_NOTIFICATION_COMMAND = 1;
	
	@Override
	protected void encode(final ChannelHandlerContext context, final SendableApnsPushNotification sendablePushNotification, final ByteBuf out) throws Exception {
		out.writeByte(PUSH_NOTIFICATION_COMMAND);
		out.writeInt(sendablePushNotification.getNotificationId());
		out.writeInt(this.getTimestampInSeconds(sendablePushNotification.getPushNotification().getExpiration()));
		out.writeShort(sendablePushNotification.getPushNotification().getTokenBytes().length);
		out.writeBytes(sendablePushNotification.getPushNotification().getTokenBytes());
		out.writeShort(sendablePushNotification.getPushNotification().getPayloadBytes().length);
		out.writeBytes(sendablePushNotification.getPushNotification().getPayloadBytes());
	}
	
	private int getTimestampInSeconds(final Date date) {
		return (int)(date.getTime() / 1000);
	}
}
