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

package com.relayrides.pushy.apns.metrics;

/**
 * <p>A metrics listener receives events from an {@link ApnsClient} that can be used to measure the performance and
 * behavior of the client. Although the information sent to a metrics listener is generally available by other means,
 * it is provided to listeners in a simplified form for ease of consumption and aggregation.</p>
 *
 * <p>The information provided to metrics listeners is intended only to measure the performance and behavior of an
 * {@code ApnsClient}; metrics listeners should never be used to drive business logic.</p>
 *
 * @see com.relayrides.pushy.apns.ApnsClient#setMetricsListener(ApnsClientMetricsListener)
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
public interface ApnsClientMetricsListener {
    void handleWriteFailure(long notificationId);
    void handleNotificationSent(long notificationId);
    void handleNotificationAccepted(long notificationId);
    void handleNotificationRejected(long notificationId);

    void handleConnectionAttemptStarted();
    void handleConnectionAttemptSucceeded();
    void handleConnectionAttemptFailed();
}
