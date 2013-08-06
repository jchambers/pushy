package com.relayrides.pushy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.util.ArrayList;
import java.util.List;

public class MockFeedbackServer {
	
	private final int port;
	
	private final ArrayList<TokenExpiration> expiredTokens;
	
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	
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
		this.workerGroup.shutdownGracefully().sync();
		this.bossGroup.shutdownGracefully().sync();
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
