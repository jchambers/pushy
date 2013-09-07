/* Copyright (c) 2013 RelayRides
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.relayrides.pushy.apns;

import static org.junit.Assert.fail;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;

import com.relayrides.pushy.apns.util.SimpleApnsPushNotification;

public abstract class BasePushyTest {
	private static final int APNS_PORT = 2195;
	private static final int FEEDBACK_PORT = 2196;
	
	private static final byte[] TOKEN = new byte[] { 0x12, 0x34, 0x56 };
	private static final String PAYLOAD = "{\"aps\":{\"alert\":\"Hello\"}}";
	private static final Date EXPIRATION = new Date(1375926408000L);
	
	private static final ApnsEnvironment TEST_ENVIRONMENT =
			new ApnsEnvironment("127.0.0.1", APNS_PORT, "127.0.0.1", FEEDBACK_PORT, false);
	
	private static final long LATCH_TIMEOUT_VALUE = 5;
	private static final TimeUnit LATCH_TIMEOUT_UNIT = TimeUnit.SECONDS;
	
	private PushManager<SimpleApnsPushNotification> pushManager;
	private ApnsClientThread<SimpleApnsPushNotification> clientThread;
	
	private MockApnsServer server;
	
	@Before
	public void setUp() throws InterruptedException {
		this.server = new MockApnsServer(APNS_PORT);
		this.server.start();
		
		this.pushManager = new PushManager<SimpleApnsPushNotification>(TEST_ENVIRONMENT, null, null);
		
		this.clientThread = new ApnsClientThread<SimpleApnsPushNotification>(this.pushManager);
		
		this.clientThread.connect();
		this.clientThread.start();
	}
	
	@After
	public void tearDown() throws InterruptedException {
		this.pushManager.shutdown();
		this.server.shutdown();
	}
	
	public PushManager<SimpleApnsPushNotification> getPushManager() {
		return this.pushManager;
	}
	
	public ApnsClientThread<SimpleApnsPushNotification> getClientThread() {
		return this.clientThread;
	}
	
	public MockApnsServer getServer() {
		return this.server;
	}
	
	public SimpleApnsPushNotification createTestNotification() {
		return new SimpleApnsPushNotification(TOKEN, PAYLOAD, EXPIRATION);
	}
	
	protected void waitForLatch(final CountDownLatch latch) throws InterruptedException {
		while (latch.getCount() > 0) {
			if (!latch.await(LATCH_TIMEOUT_VALUE, LATCH_TIMEOUT_UNIT)) {
				fail("Timed out waiting for latch.");
			}
		}
	}
}
