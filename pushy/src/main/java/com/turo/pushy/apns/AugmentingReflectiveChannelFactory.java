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

package com.turo.pushy.apns;

import io.netty.channel.Channel;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.util.AttributeKey;

import java.util.Objects;

/**
 * A channel factory that attaches a single attribute to all newly-created channels.
 *
 * @param <T> the type of channel created by this factory
 * @param <A> the type of the attribute attached to newly-constructed channels
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 */
class AugmentingReflectiveChannelFactory<T extends Channel, A> extends ReflectiveChannelFactory<T> {

    private final AttributeKey<A> attributeKey;
    private final A attributeValue;

    AugmentingReflectiveChannelFactory(final Class<? extends T> channelClass, final AttributeKey<A> attributeKey, final A attributeValue) {
        super(channelClass);

        Objects.requireNonNull(attributeKey, "Attribute key must not be null.");

        this.attributeKey = attributeKey;
        this.attributeValue = attributeValue;
    }

    @Override
    public T newChannel() {
        final T channel = super.newChannel();

        channel.attr(this.attributeKey).set(this.attributeValue);

        return channel;
    }
}
