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

package com.eatthepath.pushy.apns.util.jackson;

import com.eatthepath.pushy.apns.util.ApnsPayloadBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

/**
 * An APNs payload builder that serializes payloads with an {@link ObjectMapper}. Callers may provide their own object
 * mapper to change how the payload builder serializes custom properties.
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.14.0
 */
public class JacksonApnsPayloadBuilder extends ApnsPayloadBuilder {

    private final ObjectMapper objectMapper;

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

    /**
     * Constructs a new payload builder with a default object mapper.
     */
    public JacksonApnsPayloadBuilder() {
        this(DEFAULT_OBJECT_MAPPER);
    }

    /**
     * Constructs a new payload builder with the given object mapper.
     *
     * @param objectMapper the object mapper with which to serialize APNs payloads
     */
    public JacksonApnsPayloadBuilder(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
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
        try {
            return this.objectMapper.writeValueAsString(this.buildPayloadMap());
        } catch (final JsonProcessingException e) {
            // Reading through the ObjectMapper source, it appears that this can never actually happen when going
            // straight to a string even though it's declared as a checked exception.
            throw new RuntimeException(e);
        }
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
        try {
            return this.objectMapper.writeValueAsString(this.buildMdmPayloadMap(pushMagicValue));
        } catch (final JsonProcessingException e) {
            // Reading through the ObjectMapper source, it appears that this can never actually happen when going
            // straight to a string even though it's declared as a checked exception.
            throw new RuntimeException(e);
        }
    }
}
