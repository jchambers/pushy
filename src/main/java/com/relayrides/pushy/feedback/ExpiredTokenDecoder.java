package com.relayrides.pushy.feedback;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

import java.util.Date;
import java.util.List;

enum ExpiredTokenDecoderState {
	EXPIRATION,
	TOKEN_LENGTH,
	TOKEN
}

public class ExpiredTokenDecoder extends ReplayingDecoder<ExpiredTokenDecoderState> {

	private Date expiration;
	private byte[] token;
	
	public ExpiredTokenDecoder() {
		super(ExpiredTokenDecoderState.EXPIRATION);
	}
	
	@Override
	protected void decode(final ChannelHandlerContext context, final ByteBuf in, final List<Object> out) {
		switch (this.state()) {
			case EXPIRATION: {
				final long timestamp = (in.readInt() & 0xFFFFFFFFL) * 1000L;
				this.expiration = new Date(timestamp);
				
				this.checkpoint(ExpiredTokenDecoderState.TOKEN_LENGTH);
				
				break;
			}
			
			case TOKEN_LENGTH: {
				this.token = new byte[in.readShort() & 0x0000FFFF];
				this.checkpoint(ExpiredTokenDecoderState.TOKEN);
				
				break;
			}
			
			case TOKEN: {
				in.readBytes(this.token);
				out.add(new TokenExpiration(this.token, this.expiration));
				
				this.checkpoint(ExpiredTokenDecoderState.EXPIRATION);
				
				break;
			}
		}
	}
}
