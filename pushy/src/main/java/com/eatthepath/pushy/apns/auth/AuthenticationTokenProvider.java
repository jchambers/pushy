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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An authentication token provider provides thread-safe, non-blocking access to a shared {@link AuthenticationToken}
 * and refreshes its authentication token at regular intervals.
 */
public class AuthenticationTokenProvider implements Closeable {

    private final ApnsSigningKey signingKey;
    private final Clock clock;

    private volatile AuthenticationToken token;

    private final ScheduledFuture<?> refreshTokenFuture;

    private static final Logger log = LoggerFactory.getLogger(AuthenticationTokenProvider.class);

    /**
     * Constructs a new authentication token provider that will generate authentication tokens using the given signing
     * key and refresh the token at the given interval. Once constructed, callers <em>must</em> call the constructed
     * instance's {@link #close()} method to cleanly dispose of the instance.
     *
     * @param signingKey the signing key to use to generate authentication tokens
     * @param maxTokenAge the maximum age of an authentication token before a new token will be generated
     * @param scheduledExecutorService an executor service to use to schedule token refresh tasks
     */
    public AuthenticationTokenProvider(final ApnsSigningKey signingKey, final Duration maxTokenAge, final ScheduledExecutorService scheduledExecutorService) {
        this(signingKey, maxTokenAge, scheduledExecutorService, Clock.systemUTC());
    }

    /**
     * Constructs a new authentication token provider that will generate authentication tokens using the given signing
     * key and refresh the token at the given interval. Once constructed, callers <em>must</em> call the constructed
     * instance's {@link #close()} method to cleanly dispose of the instance.
     *
     * @param signingKey the signing key to use to generate authentication tokens
     * @param maxTokenAge the maximum age of an authentication token before a new token will be generated
     * @param scheduledExecutorService an executor service to use to schedule token refresh tasks
     * @param clock the clock to use to schedule tasks and manage token timestamps
     */
    AuthenticationTokenProvider(final ApnsSigningKey signingKey, final Duration maxTokenAge, final ScheduledExecutorService scheduledExecutorService, final Clock clock) {
        this.signingKey = signingKey;
        this.clock = clock;

        this.token = new AuthenticationToken(signingKey, clock.instant());

        this.refreshTokenFuture = scheduledExecutorService.scheduleAtFixedRate(this::refreshToken, maxTokenAge.toMillis(), maxTokenAge.toMillis(), TimeUnit.MILLISECONDS);
    }

    void refreshToken() {
        log.debug("Refreshed authentication token");
        this.token = new AuthenticationToken(signingKey, clock.instant());
    }

    /**
     * Returns a current authentication token. Subsequent calls to this method may, but are not guaranteed to, return
     * the same token because tokens are refreshed at regular intervals. This method is thread-safe and can be called by
     * any number of concurrent consumers.
     *
     * @return a current authentication token
     */
    public AuthenticationToken getAuthenticationToken() {
        return this.token;
    }

    /**
     * Shuts down this token provider, cancelling any recurring jobs to refresh tokens. Callers <em>must</em> call this
     * method to cleanly dispose of an authentication token provider.
     */
    public void close() {
        this.refreshTokenFuture.cancel(false);
    }
}
