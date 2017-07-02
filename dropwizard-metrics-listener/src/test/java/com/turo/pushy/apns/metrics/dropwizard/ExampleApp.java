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

package com.turo.pushy.apns.metrics.dropwizard;

import com.codahale.metrics.MetricRegistry;
import com.turo.pushy.apns.ApnsClient;
import com.turo.pushy.apns.ApnsClientBuilder;

public class ExampleApp {

    @SuppressWarnings("unused")
    public static void main(final String[] args) throws Exception {
        // Note that a DropwizardApnsClientMetricsListener is intended for use
        // with only one ApnsClient at a time; if you're constructing multiple
        // clients with the same builder, you'll need to specify a new listener
        // for each client.
        final DropwizardApnsClientMetricsListener listener =
                new DropwizardApnsClientMetricsListener();

        final ApnsClient apnsClient = new ApnsClientBuilder()
                .setMetricsListener(listener)
                .build();

        final MetricRegistry registry = new MetricRegistry();

        // DropwizardApnsClientMetricsListeners are themselves Metrics and can
        // be added to a registry.
        registry.register(MetricRegistry.name(ExampleApp.class, "apnsClient"), listener);
    }
}
