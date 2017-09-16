/*
 * Copyright (c) 2013-2017 Turo
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

package com.turo.pushy.apns.server;

import java.util.Objects;
import java.util.UUID;

/**
 * An exception thrown by {@link PushNotificationHandler} instances to indicate that a push notification should be
 * rejected by the server. Handlers that need to reject a notification because its destination device token is no longer
 * valid should throw an {@link UnregisteredDeviceTokenException} instead.
 *
 * @since 0.12
 */
public class RejectedNotificationException extends Exception {
    private final RejectionReason errorReason;
    private final UUID apnsId;

    /**
     * Constructs a new rejected notification exception with the given rejection reason and notification identifier.
     *
     * @param rejectionReason the reason for the rejection
     * @param apnsId the notification identifier provided by the client that sent the notification; may be {@code null}
     * if the client did not provide an identifier or the identifier was invalid, in which case a server-generated
     * identifier will be used instead
     */
    public RejectedNotificationException(final RejectionReason rejectionReason, final UUID apnsId) {
        Objects.requireNonNull(rejectionReason, "Error reason must not be null.");

        this.errorReason = rejectionReason;
        this.apnsId = apnsId != null ? apnsId : UUID.randomUUID();
    }

    RejectionReason getRejectionReason() {
        return errorReason;
    }

    UUID getApnsId() {
        return apnsId;
    }
}
