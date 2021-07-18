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

package com.eatthepath.pushy.apns.util;

/**
 * An enumeration of interruption levels that may be specified in a notification payload. Interruption levels are
 * supported in iOS 15 and newer.
 *
 * @see <a href="https://developer.apple.com/design/human-interface-guidelines/ios/system-capabilities/notifications/">Human
 * Interface Guidelines: Notifications</a>
 *
 * @since 0.15
 */
public enum InterruptionLevel {
    /**
     * According to Apple's Human Interface Guidelines, a {@code passive} notification contains "information people can
     * view at their leisure, like a restaurant recommendation."
     */
    PASSIVE("passive"),

    /**
     * According to Apple's Human Interface Guidelines, an {@code active} notification contains "information people
     * might appreciate knowing about when it arrives, like a score update on their favorite sports team." If no
     * interruption level is specified, the notification is assumed to have an {@code active} interruption level.
     */
    ACTIVE("active"),

    /**
     * According to Apple's Human Interface Guidelines, a {@code time-sensitive} notification contains "information that
     * directly impacts the user and requires their immediate attention, like an account security issue or a package
     * delivery."
     */
    TIME_SENSITIVE("time-sensitive"),

    /**
     * According to Apple's Human Interface guidelines, a {@code critical} notification contains:
     *
     * <blockquote>…urgent information about personal health and public safety that directly impacts the user and demands
     * their immediate attention. Critical notifications are extremely rare and typically come from governmental and
     * public agencies or healthcare apps. You must get an entitlement to use the Critical interruption
     * level.</blockquote>
     */
    CRITICAL("critical");

    private final String value;

    InterruptionLevel(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
