package com.relayrides.pushy.apns.util;

import org.apache.commons.lang3.RandomStringUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ApnsPayloadBuilderBenchmark {

    private ApnsPayloadBuilder apnsPayloadBuilder;

    private static final int MAXIMUM_PAYLOAD_SIZE = 1024 * 8;

    private String shortAsciiMessageBody;
    private String longAsciiMessageBody;

    @Setup
    public void setUp() {
        this.apnsPayloadBuilder = new ApnsPayloadBuilder();

        this.shortAsciiMessageBody = RandomStringUtils.randomAscii(MAXIMUM_PAYLOAD_SIZE / 8);
        this.longAsciiMessageBody = RandomStringUtils.randomAscii(MAXIMUM_PAYLOAD_SIZE * 2);
    }

    @Benchmark
    public String testShortAsciiMessageBody() {
        this.apnsPayloadBuilder.setAlertBody(this.shortAsciiMessageBody);
        return this.apnsPayloadBuilder.buildWithMaximumLength(MAXIMUM_PAYLOAD_SIZE);
    }

    @Benchmark
    public String testLongAsciiMessageBody() {
        this.apnsPayloadBuilder.setAlertBody(this.longAsciiMessageBody);
        return this.apnsPayloadBuilder.buildWithMaximumLength(MAXIMUM_PAYLOAD_SIZE);
    }
}
