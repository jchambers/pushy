# Dropwizard Metrics listener for Pushy

This module is an implementation of Pushy's [`ApnsClientMetricsListener`](http://relayrides.github.io/pushy/apidocs/0.13/com/relayrides/pushy/apns/ApnsClientMetricsListener.html) interface that uses the [Dropwizard Metrics library](http://metrics.dropwizard.io/) to gather and report metrics. If you use [Maven](http://maven.apache.org/), you can add the listener to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.turo</groupId>
    <artifactId>pushy-dropwizard-metrics-listener</artifactId>
    <version>0.13.0</version>
</dependency>
```

If you don't use Maven, you can add the `.jar` file and its dependencies to your classpath by the method of your choice. The Dropwizard Metrics listener for Pushy depends on Pushy itself (obviously enough) and version 3.1.0 of the [Dropwizard Metrics library](http://metrics.dropwizard.io/).

## Using the Dropwizard Metrics listener

Creating new Dropwizard Metrics listeners is straightforward. To get started, construct a new listener and pass it in when constructing a new client.

```java
final DropwizardApnsClientMetricsListener listener =
        new DropwizardApnsClientMetricsListener();

final ApnsClient apnsClient = new ApnsClientBuilder()
        .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
        .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                "TEAMID1234", "KEYID67890"))
        .setMetricsListener(listener)
        .build();
```

Note that a `DropwizardApnsClientMetricsListener` is intended for use with only one `ApnsClient` at a time; if you're constructing multiple clients with the same builder, you'll need to specify a new listener for each client.

`DropwizardApnsClientMetricsListeners` are themselves `Metrics`, and can be registered with a `MetricRegistry`.

```java
registry.register("com.example.MyApnsClient", listener);
```

## License

The Dropwizard Metrics listener for Pushy is available under the [MIT License](http://opensource.org/licenses/MIT).
