package com.relayrides.pushy.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.relayrides.pushy.apns.ApnsEnvironment;
import com.relayrides.pushy.feedback.FeedbackServiceClient;
import com.relayrides.pushy.feedback.TokenExpiration;

public class FeedbackServiceClientTest {
	
	private static final int APNS_PORT = 2195;
	private static final int FEEDBACK_PORT = 2196;
	
	private MockFeedbackServer feedbackServer;
	private FeedbackServiceClient feedbackClient;
	
	@Before
	public void setUp() throws InterruptedException {
		this.feedbackServer = new MockFeedbackServer(FEEDBACK_PORT);
		this.feedbackServer.start();
		
		this.feedbackClient = new FeedbackServiceClient(
				new ApnsEnvironment("127.0.0.1", APNS_PORT, "127.0.0.1", FEEDBACK_PORT, false));
	}

	@Test
	public void testGetExpiredTokens() throws InterruptedException {
		assertTrue(feedbackClient.getExpiredTokens().isEmpty());
		
		// Dates will have some loss of precision since APNS only deals with SECONDS since the epoch; we choose
		// timestamps that just happen to be on full seconds.
		final TokenExpiration firstToken = new TokenExpiration(new byte[] { 97, 44, 32, 16, 16 }, new Date(1375760188000L));
		final TokenExpiration secondToken = new TokenExpiration(new byte[] { 77, 62, 40, 30, 8 }, new Date(1375760188000L));
		
		this.feedbackServer.addExpiredToken(firstToken);
		this.feedbackServer.addExpiredToken(secondToken);
		
		final List<TokenExpiration> expiredTokens = this.feedbackClient.getExpiredTokens();
		
		assertEquals(2, expiredTokens.size());
		assertTrue(expiredTokens.contains(firstToken));
		assertTrue(expiredTokens.contains(secondToken));
		
		assertTrue(feedbackClient.getExpiredTokens().isEmpty());
	}

	@After
	public void tearDown() throws InterruptedException {
		this.feedbackServer.shutdown();
		this.feedbackClient.destroy();
	}
}
