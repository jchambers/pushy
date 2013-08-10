package com.relayrides.pushy.apns.feedback;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.ArrayList;
import java.util.List;

import com.relayrides.pushy.apns.feedback.TokenExpiration;

public class MockFeedbackServer {
	
	private final int port;
	
	private final ArrayList<TokenExpiration> expiredTokens;
	
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	
	private class ExpiredTokenEncoder extends MessageToByteEncoder<TokenExpiration> {

		@Override
		protected void encode(final ChannelHandlerContext context, final TokenExpiration expiredToken, final ByteBuf out) {
			out.writeInt((int) (expiredToken.getExpiration().getTime() / 1000L));
			out.writeShort(expiredToken.getToken().length);
			out.writeBytes(expiredToken.getToken());
		}
	}
	
	private class MockFeedbackServerHandler extends ChannelInboundHandlerAdapter {
		
		private final MockFeedbackServer feedbackServer;
		
		public MockFeedbackServerHandler(final MockFeedbackServer feedbackServer) {
			this.feedbackServer = feedbackServer;
		}
		
		@Override
	    public void channelActive(final ChannelHandlerContext context) {
			
			final List<TokenExpiration> expiredTokens = this.feedbackServer.getAndClearAllExpiredTokens();
			
			ChannelFuture lastWriteFuture = null;
			
			for (final TokenExpiration expiredToken : expiredTokens) {
				lastWriteFuture = context.writeAndFlush(expiredToken);
			}
			
			if (lastWriteFuture != null) {
				lastWriteFuture.addListener(new GenericFutureListener<ChannelFuture>() {

					public void operationComplete(final ChannelFuture future) {
						context.close();
					}
					
				});
			} else {
				context.close();
			}
		}
	}
	
	public MockFeedbackServer(final int port) {
		this.port = port;
		
		this.expiredTokens = new ArrayList<TokenExpiration>();
	}
	
	public void start() throws InterruptedException {
		this.bossGroup = new NioEventLoopGroup();
		this.workerGroup = new NioEventLoopGroup();
		
		final ServerBootstrap bootstrap = new ServerBootstrap();
		
		bootstrap.group(this.bossGroup, this.workerGroup);
		bootstrap.channel(NioServerSocketChannel.class);
		
		final MockFeedbackServer server = this;
		bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {

			@Override
			protected void initChannel(final SocketChannel channel) throws Exception {
				channel.pipeline().addLast("encoder", new ExpiredTokenEncoder());
				channel.pipeline().addLast("handler", new MockFeedbackServerHandler(server));
			}
		});
		
		bootstrap.bind(this.port).sync();
	}
	
	public void shutdown() throws InterruptedException {
		this.workerGroup.shutdownGracefully();
		this.bossGroup.shutdownGracefully();
	}
	
	public synchronized void addExpiredToken(final TokenExpiration expiredToken) {
		this.expiredTokens.add(expiredToken);
	}
	
	protected synchronized List<TokenExpiration> getAndClearAllExpiredTokens() {
		final ArrayList<TokenExpiration> tokensToReturn = new ArrayList<TokenExpiration>(this.expiredTokens);
		this.expiredTokens.clear();
		
		return tokensToReturn;
	}
}
