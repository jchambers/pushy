package com.relayrides.pushy;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

enum ApnsPushNotificationDecoderState {
	OPCODE,
	IDENTIFIER,
	EXPIRATION,
	TOKEN_LENGTH,
	TOKEN,
	PAYLOAD_LENGTH,
	PAYLOAD
}

public class ApnsPushNotificationDecoder extends ReplayingDecoder<ApnsPushNotificationDecoderState> {

	private int identifier;
	private Date expiration;
	private byte[] token;
	private byte[] payload;
	
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
					// TODO Close connection and respond with error
				}
				
				this.checkpoint(ApnsPushNotificationDecoderState.IDENTIFIER);
				
				break;
			}
			
			case IDENTIFIER: {
				this.identifier = in.readInt();
				this.checkpoint(ApnsPushNotificationDecoderState.EXPIRATION);
				
				break;
			}
			
			case EXPIRATION: {
				final long timestamp = ((long) (in.readInt() & 0xFFFFFFFFL)) * 1000L;
				this.expiration = new Date(timestamp);
				
				this.checkpoint(ApnsPushNotificationDecoderState.TOKEN_LENGTH);
				
				break;
			}
			
			case TOKEN_LENGTH: {
				this.token = new byte[in.readShort() & 0x0000FFFF];
				this.checkpoint(ApnsPushNotificationDecoderState.TOKEN);
				
				break;
			}
			
			case TOKEN: {
				in.readBytes(this.token);
				this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD_LENGTH);
				
				break;
			}
			
			case PAYLOAD_LENGTH: {
				this.payload = new byte[in.readShort() & 0x0000FFFF];
				this.checkpoint(ApnsPushNotificationDecoderState.PAYLOAD);
				
				break;
			}
			
			case PAYLOAD: {
				in.readBytes(this.payload);
				
				String payloadString;
				
				try {
					payloadString = new String(this.payload, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					// This should never happen for a literally-specified UTF-8 encoding
					throw new RuntimeException(e);
				}
				
				out.add(new SimpleApnsPushNotification(this.token, payloadString, this.expiration));
				this.checkpoint(ApnsPushNotificationDecoderState.OPCODE);
				
				break;
			}
		}
	}
}
