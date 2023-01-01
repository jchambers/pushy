# Jackson APNs payload builder for Pushy

This module provides an [`ApnsPayloadBuilder`](https://pushy-apns.org/apidocs/0.14/com/eatthepath/pushy/apns/util/ApnsPayloadBuilder.html) that uses [Jackson](https://github.com/FasterXML/jackson) to serialize APNs payloads. If you use [Maven](http://maven.apache.org/), you can add the payload builder to your project by adding the following dependency declaration to your POM:

```xml
<dependency>
    <groupId>com.eatthepath</groupId>
    <artifactId>pushy-jackson-payload-builder</artifactId>
    <version>0.15.2</version>
</dependency>
```

If you don't use Maven, you can add the `.jar` file and its dependencies to your classpath by the method of your choice. The Jackson payload builder for Pushy depends on Pushy itself (obviously enough) and version 2.12 of the [Jackson databind library](https://github.com/FasterXML/jackson-databind).

## Using the Jackson payload builder

Callers can construct a payload builder that uses a default Jackson object mapper with `JacksonPayloadBuilder`'s no-argument constructor:

```java
final ApnsPayloadBuilder jacksonPayloadBuilder =
        new JacksonApnsPayloadBuilder();

jacksonPayloadBuilder.setAlertBody("Hello from Jackson!");

final String payload = jacksonPayloadBuilder.build();
```

Callers can also provide their own object mapper to customize how it serializes payloads:

```java
final ObjectMapper objectMapper = new ObjectMapper();
objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

final ApnsPayloadBuilder customizedJacksonPayloadBuilder =
        new JacksonApnsPayloadBuilder(objectMapper);
```

## License

The Jackson payload builder for Pushy is available under the [MIT License](http://opensource.org/licenses/MIT).
