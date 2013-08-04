package com.relayrides.pushy;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class PushQueue {
	private final BlockingQueue<ApnsPushNotification> queue;
	
	private final String host;
	private final int port;
	
	public PushQueue(final String host, final int port) {
		this.queue = new LinkedBlockingQueue<ApnsPushNotification>();
		
		this.host = host;
		this.port = port;
	}
	
	public void start() throws InterruptedException {
		// TODO
	}
	
	public void shutdown() {
		// TODO
	}
}
