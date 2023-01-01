# Gson APNs payload builder for Pushy

This module provides an [`ApnsPayloadBuilder`](https://pushy-apns.org/apidocs/0.14/com/eatthepath/pushy/apns/util/ApnsPayloadBuilder.html) that uses [Gson](https://github.com/google/gson) to serialize APNs payloads. If you use [Maven](http://maven.apache.org/), you can add the payload builder to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.eatthepath</groupId>
    <artifactId>pushy-gson-payload-builder</artifactId>
    <version>0.15.2</version>
</dependency>
```

If you don't use Maven, you can add the `.jar` file and its dependencies to your classpath by the method of your choice. The Gson payload builder for Pushy depends on Pushy itself (obviously enough) and version 2.8.6 of the [Gson library](https://github.com/google/gson).

## Using the Gson payload builder

Callers can construct a payload builder that uses a default Gson instance with `GsonPayloadBuilder`'s no-argument constructor:

```java
final ApnsPayloadBuilder gsonPayloadBuilder =
        new GsonApnsPayloadBuilder();

gsonPayloadBuilder.setAlertBody("Hello from GSON!");

final String payload = gsonPayloadBuilder.build();
```

Callers can also provide their own Gson instance to customize how it serializes payloads:

```java
final ApnsPayloadBuilder gsonPayloadBuilder =
        new GsonApnsPayloadBuilder(new GsonBuilder()
                .disableHtmlEscaping()
                .setDateFormat(DateFormat.SHORT, DateFormat.MEDIUM)
                .create());
```

## License

The Gson payload builder for Pushy is available under the [MIT License](http://opensource.org/licenses/MIT).
