package com.relayrides.pushy.apns;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.Charset;
import java.util.Date;

public class PushNotificationEncoder extends MessageToByteEncoder<SendableApnsPushNotification> {

	private static final byte ENHANCED_PUSH_NOTIFICATION_COMMAND = 1;
	private static final int EXPIRE_IMMEDIATELY = 0;
	
	private final Charset utf8 = Charset.forName("UTF-8");
	
	@Override
	protected void encode(final ChannelHandlerContext context, final SendableApnsPushNotification sendablePushNotification, final ByteBuf out) throws Exception {
		out.writeByte(ENHANCED_PUSH_NOTIFICATION_COMMAND);
		out.writeInt(sendablePushNotification.getNotificationId());
		
		if (sendablePushNotification.getPushNotification().getExpiration() != null) {
			out.writeInt(this.getTimestampInSeconds(sendablePushNotification.getPushNotification().getExpiration()));
		} else {
			out.writeInt(EXPIRE_IMMEDIATELY);
		}
		
		out.writeShort(sendablePushNotification.getPushNotification().getToken().length);
		out.writeBytes(sendablePushNotification.getPushNotification().getToken());
		
		final byte[] payloadBytes = sendablePushNotification.getPushNotification().getPayload().getBytes(utf8);
		
		out.writeShort(payloadBytes.length);
		out.writeBytes(payloadBytes);
	}
	
	private int getTimestampInSeconds(final Date date) {
		return (int)(date.getTime() / 1000);
	}
}
