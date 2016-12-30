package com.relayrides.pushy.apns.metrics.dropwizard;

import com.codahale.metrics.MetricRegistry;
import com.relayrides.pushy.apns.ApnsClient;
import com.relayrides.pushy.apns.ApnsClientBuilder;

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
        registry.register("com.example.MyApnsClient", listener);
    }
}
