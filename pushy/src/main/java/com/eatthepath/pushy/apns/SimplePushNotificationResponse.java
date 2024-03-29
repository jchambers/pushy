/*
 * Copyright (c) 2020 Jon Chambers
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

package com.eatthepath.pushy.apns;

import com.eatthepath.uuid.FastUUID;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A trivial and immutable implementation of the {@link PushNotificationResponse} interface.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class SimplePushNotificationResponse<T extends ApnsPushNotification> implements PushNotificationResponse<T> {
    private final T pushNotification;
    private final boolean success;
    private final UUID apnsId;
    private final UUID apnsUniqueId;
    private final int statusCode;
    private final String rejectionReason;
    private final Instant tokenExpirationTimestamp;

    SimplePushNotificationResponse(final T pushNotification, final boolean success, final UUID apnsId, final UUID apnsUniqueId, final int statusCode, final String rejectionReason, final Instant tokenExpirationTimestamp) {
        this.pushNotification = pushNotification;
        this.success = success;
        this.apnsId = apnsId;
        this.apnsUniqueId = apnsUniqueId;
        this.statusCode = statusCode;
        this.rejectionReason = rejectionReason;
        this.tokenExpirationTimestamp = tokenExpirationTimestamp;
    }

    @Override
    public T getPushNotification() {
        return this.pushNotification;
    }

    @Override
    public boolean isAccepted() {
        return this.success;
    }

    @Override
    public UUID getApnsId() {
        return this.apnsId;
    }

    @Override
    public Optional<UUID> getApnsUniqueId() {
        return Optional.ofNullable(apnsUniqueId);
    }

    @Override
    public int getStatusCode() {
        return this.statusCode;
    }

    @Override
    public Optional<String> getRejectionReason() {
        return Optional.ofNullable(this.rejectionReason);
    }

    @Override
    public Optional<Instant> getTokenInvalidationTimestamp() {
        return Optional.ofNullable(this.tokenExpirationTimestamp);
    }

    @Override
    public String toString() {
        return "SimplePushNotificationResponse{" +
                "pushNotification=" + pushNotification +
                ", success=" + success +
                ", apnsId=" + (apnsId != null ? FastUUID.toString(apnsId) : null) +
                ", apnsUniqueId=" + getApnsUniqueId().map(FastUUID::toString).orElse(null) +
                ", rejectionReason='" + rejectionReason + '\'' +
                ", tokenExpirationTimestamp=" + tokenExpirationTimestamp +
                '}';
    }
}
