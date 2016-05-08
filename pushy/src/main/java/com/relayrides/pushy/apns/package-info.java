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

/**
 * <p>Contains classes and interfaces for interacting with the Apple Push Notification service (APNs).</p>
 *
 * <p>Callers will primarily interact with the {@link com.relayrides.pushy.apns.ApnsClient} class to send push
 * notifications. An {@code ApnsClient} maintains a single connection to the APNs gateway and sends notifications using
 * the HTTP/2-based APNs protocol. Notifications are sent asynchronously.</p>
 *
 * <p>The {@link com.relayrides.pushy.apns.ApnsPushNotification} interface represents a single APNs push notification
 * sent to a single device. A simple concrete implementation of the {@code ApnsPushNotification} interface
 * ({@link com.relayrides.pushy.apns.util.SimpleApnsPushNotification}) and tools for constructing push notification
 * payloads can be found in the {@code com.relayrides.pushy.apns.util} package.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
package com.relayrides.pushy.apns;
