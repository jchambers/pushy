# Micrometer Metrics listener for Pushy

This module is an implementation of Pushy's [`ApnsClientMetricsListener`](https://pushy-apns.org/apidocs/0.14/com/eatthepath/pushy/apns/ApnsClientMetricsListener.html) interface that uses the [Micrometer application monitoring library](http://micrometer.io/) to gather and report metrics. If you use [Maven](http://maven.apache.org/), you can add the listener to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.eatthepath</groupId>
    <artifactId>pushy-micrometer-metrics-listener</artifactId>
    <version>0.16.0</version>
</dependency>
```

If you don't use Maven, you can add the `.jar` file and its dependencies to your classpath by the method of your choice. The Micrometer listener for Pushy depends on Pushy itself (obviously enough) and version 1.13.1 of the [Micrometer application monitoring library](http://micrometer.io/).

## Using the Micrometer Metrics listener

Creating new Micrometer listeners is straightforward. To get started, construct a new listener by passing an existing `MeterRegistry` (and an optional list of [tags](http://micrometer.io/docs/concepts#_naming_meters)) to the `MicrometerApnsClientMetricsListener` constructor. From there, construct a new `ApnsClient` with the metric listener:

```java
final MicrometerApnsClientMetricsListener listener =
        new MicrometerApnsClientMetricsListener(existingMeterRegistry);

final ApnsClient apnsClient = new ApnsClientBuilder()
        .setApnsServer(ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
        .setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File("/path/to/key.p8"),
                "TEAMID1234", "KEYID67890"))
        .setMetricsListener(listener)
        .build();
```

Note that a `MicrometerApnsClientMetricsListener` is intended for use with only one `ApnsClient` at a time; if you're constructing multiple clients with the same builder, you'll need to specify a new listener for each client and should consider supplying identifying tags to each listener.

## License

The Micrometer metrics listener for Pushy is available under the [MIT License](http://opensource.org/licenses/MIT).
