package com.relayrides.pushy.apns.util;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ApnsPayloadBuilderBenchmark {

    private ApnsPayloadBuilder apnsPayloadBuilder;

    private static final int MAXIMUM_PAYLOAD_SIZE = 1024 * 8;

    private static final String SHORT_MESSAGE_BODY = "This is a short message body.";

    @Setup
    public void setUp() {
        this.apnsPayloadBuilder = new ApnsPayloadBuilder();
    }

    @Benchmark
    public String testShortUnabbreviatedMessageBody() {
        this.apnsPayloadBuilder.setAlertBody(SHORT_MESSAGE_BODY);
        return this.apnsPayloadBuilder.buildWithMaximumLength(MAXIMUM_PAYLOAD_SIZE);
    }
}
