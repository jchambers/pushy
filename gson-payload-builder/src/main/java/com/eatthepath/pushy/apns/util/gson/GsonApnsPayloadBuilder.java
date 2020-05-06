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

package com.eatthepath.pushy.apns.util.gson;

import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Objects;

/**
 * An APNs payload builder that serializes payloads with a {@link Gson} instance. Callers may provide their own
 * {@code Gson} instance to change how the payload builder serializes custom properties.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.14.0
 */
public class GsonApnsPayloadBuilder extends ApnsPayloadBuilder {

    private final Gson gson;

    private static final Gson DEFAULT_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    /**
     * Constructs a new payload builder with a default {@link Gson} instance.
     */
    public GsonApnsPayloadBuilder() {
        this(DEFAULT_GSON);
    }

    /**
     * Constructs a new payload builder with the given {@code Gson} instance.
     *
     * @param gson the {@code Gson} instance with which to serialize APNs payloads
     */
    public GsonApnsPayloadBuilder(final Gson gson) {
        this.gson = Objects.requireNonNull(gson);
    }

    /**
     * Returns a JSON representation of the push notification payload under construction.
     *
     * @return a JSON representation of the payload under construction
     *
     * @see #buildPayloadMap()
     *
     * @since 0.14.0
     */
    @Override
    public String build() {
        return this.gson.toJson(this.buildPayloadMap());
    }

    /**
     * Returns a JSON representation of a
     * <a href="https://developer.apple.com/library/content/documentation/Miscellaneous/Reference/MobileDeviceManagementProtocolRef/1-Introduction/Introduction.html#//apple_ref/doc/uid/TP40017387-CH1-SW1">Mobile
     * Device Management</a> "wake up" payload.
     *
     * @param pushMagicValue the "push magic" string that the device sends to the MDM server in a {@code TokenUpdate}
     * message
     *
     * @return a JSON representation of an MDM "wake up" notification payload
     *
     * @see <a href="https://developer.apple.com/library/content/documentation/Miscellaneous/Reference/MobileDeviceManagementProtocolRef/3-MDM_Protocol/MDM_Protocol.html#//apple_ref/doc/uid/TP40017387-CH3-SW2">Mobile
     * Device Management (MDM) Protocol</a>
     *
     * @since 0.14.0
     *
     * @see #buildMdmPayloadMap(String)
     */
    @Override
    public String buildMdmPayload(final String pushMagicValue) {
        return this.gson.toJson(this.buildMdmPayloadMap(pushMagicValue));
    }
}
