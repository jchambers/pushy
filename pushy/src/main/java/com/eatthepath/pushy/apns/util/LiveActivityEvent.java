/*
 * Copyright (c) 2022 Jon Chambers
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

package com.eatthepath.pushy.apns.util;

/**
 * An enumeration of accepted actions for Live Activity notification payloads.
 *
 * @see <a href="https://developer.apple.com/documentation/activitykit/starting-and-updating-live-activities-with-activitykit-push-notifications#Construct-the-payload-that-starts-a-Live-Activity">Starting and updating Live Activities with ActivityKit push notifications</a>
 *
 * @since 0.15.2
*/
public enum LiveActivityEvent {
    /**
     * Used to start a Live Activity.
     */
    START("start"),

    /**
     * Used to update a Live Activity.
     */
    UPDATE("update"),

    /**
     * Used to end a Live Activity.
     */
    END("end");

    private final String value;

    LiveActivityEvent(final String value) {
        this.value = value;
    }

    String getValue() {
        return value;
    }
}
