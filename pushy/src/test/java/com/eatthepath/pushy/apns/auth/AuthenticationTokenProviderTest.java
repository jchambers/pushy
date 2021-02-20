/*
 * Copyright (c) 2021 Jon Chambers
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

package com.eatthepath.pushy.apns.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AuthenticationTokenProviderTest {

    private ScheduledExecutorService scheduledExecutorService;
    private SettableClock clock;

    private AuthenticationTokenProvider authenticationTokenProvider;

    private static final String KEY_ID = "TESTKEY123";
    private static final String TEAM_ID = "TEAMID0987";

    private static class SettableClock extends Clock {

        private Instant instant;

        public SettableClock(final Instant initialTime) {
            this.instant = initialTime;
        }

        public void setInstant(final Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, InvalidKeyException {
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.clock = new SettableClock(Instant.now());

        final ApnsSigningKey signingKey = new ApnsSigningKey(KEY_ID, TEAM_ID, (ECPrivateKey) KeyPairUtil.generateKeyPair().getPrivate());

        this.authenticationTokenProvider = new AuthenticationTokenProvider(signingKey, Duration.ofMinutes(50), scheduledExecutorService, clock);
    }

    @AfterEach
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void tearDown() throws InterruptedException {
        authenticationTokenProvider.close();

        scheduledExecutorService.shutdown();
        scheduledExecutorService.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void testRefreshToken() {
        final Instant initialTime = clock.instant();
        final AuthenticationToken initialToken = authenticationTokenProvider.getAuthenticationToken();

        final Instant laterTime = initialTime.plus(Duration.ofHours(1));
        clock.setInstant(laterTime);
        authenticationTokenProvider.refreshToken();

        assertNotEquals(initialToken, authenticationTokenProvider.getAuthenticationToken());
        assertEquals(laterTime, authenticationTokenProvider.getAuthenticationToken().getIssuedAt());
    }

    @Test
    void getAuthenticationToken() {
        final AuthenticationToken authenticationToken = authenticationTokenProvider.getAuthenticationToken();

        assertEquals(KEY_ID, authenticationToken.getKeyId());
        assertEquals(TEAM_ID, authenticationToken.getTeamId());
        assertEquals(clock.instant(), authenticationToken.getIssuedAt());
    }
}
