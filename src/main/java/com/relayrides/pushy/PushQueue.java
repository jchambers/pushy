package com.relayrides.pushy;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.ApnsEnvironment;

public class PushQueue<T extends ApnsPushNotification> {
	private final BlockingQueue<T> queue;
	
	private final ApnsClient client;
	
	protected PushQueue(final ApnsEnvironment environment, final KeyStore keyStore, final char[] keyStorePassword) {
		this.queue = new LinkedBlockingQueue<T>();
		this.client = new ApnsClient(environment, keyStore, keyStorePassword, this.queue);
	}
	
	public synchronized void start() throws InterruptedException {
		this.client.start();
	}
	
	public synchronized List<T> shutdown() {
		this.client.shutdown();
		return new ArrayList<T>(this.queue);
	}
}
