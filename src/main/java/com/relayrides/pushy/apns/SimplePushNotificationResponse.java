/* Copyright (c) 2013-2016 RelayRides
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
 * THE SOFTWARE. */

package com.relayrides.pushy.apns;

import java.util.Date;

/**
 * A trivial and immutable implementation of the {@link PushNotificationResponse} interface.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class SimplePushNotificationResponse<T extends ApnsPushNotification> implements PushNotificationResponse<T> {
    private final T pushNotification;
    private final boolean success;
    private final String rejectionReason;
    private final Date tokenExpirationTimestamp;

    public SimplePushNotificationResponse(final T pushNotification, final boolean success, final String rejectionReason, final Date tokenExpirationTimestamp) {
        this.pushNotification = pushNotification;
        this.success = success;
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
    public String getRejectionReason() {
        return this.rejectionReason;
    }

    @Override
    public Date getTokenInvalidationTimestamp() {
        return this.tokenExpirationTimestamp;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SimplePushNotificationResponse [pushNotification=");
        builder.append(this.pushNotification);
        builder.append(", success=");
        builder.append(this.success);
        builder.append(", rejectionReason=");
        builder.append(this.rejectionReason);
        builder.append(", tokenExpirationTimestamp=");
        builder.append(this.tokenExpirationTimestamp);
        builder.append("]");
        return builder.toString();
    }
}
