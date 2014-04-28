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

/**
 * <p>Contains classes for working with APNs tokens and payloads.</p>
 *
 * <p>Push notification payloads are <a href="http://json.org/">JSON</a> strings that contain information about how the
 * receiving device should handle and display the notification. The
 * {@link com.relayrides.pushy.apns.util.ApnsPayloadBuilder} class is a tool to construct payloads that comply with the
 * APNs specification.</p>
 *
 * <p>Device tokens identify the device to which a push notification is being sent. Ultimately, tokens need to be
 * expressed as an array of bytes, but a common practice is to transmit tokens from a device to a push notification
 * provider as a string of hexadecimal characters (e.g. the output of
 * <a href="https://developer.apple.com/library/mac/documentation/Cocoa/Reference/Foundation/Classes/NSData_Class/Reference/Reference.html#//apple_ref/occ/instm/NSData/description">
 * {@code [NSData describe]}</a>). The {@link com.relayrides.pushy.apns.util.TokenUtil} class provides methods for
 * converting tokens between hexadecimal strings and byte arrays.</p>
 *
 * @author <a href="mailto:jon@relayrides.com">Jon Chambers</a>
 */
package com.relayrides.pushy.apns.util;
