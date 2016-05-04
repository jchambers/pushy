package com.relayrides.pushy.apns.util;

import java.util.ArrayList;
import java.util.List;

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
    private String shortChineseMessageBody;
    private String longChineseMessageBody;

    @Setup
    public void setUp() {
        this.apnsPayloadBuilder = new ApnsPayloadBuilder();

        this.shortAsciiMessageBody = RandomStringUtils.randomAscii(MAXIMUM_PAYLOAD_SIZE / 8);
        this.longAsciiMessageBody = RandomStringUtils.randomAscii(MAXIMUM_PAYLOAD_SIZE * 2);

        final char[] chineseCharacters;
        {
            final List<Character> cjkUnifiedIdeographs = new ArrayList<>(80388);

            for (int codePoint = Character.MIN_CODE_POINT; codePoint < Character.MAX_CODE_POINT; codePoint++) {
                if (Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(Character.UnicodeBlock.of(codePoint))) {
                    cjkUnifiedIdeographs.add((char) codePoint);
                }
            }

            chineseCharacters = new char[cjkUnifiedIdeographs.size()];

            for (int i = 0; i < cjkUnifiedIdeographs.size(); i++) {
                chineseCharacters[i] = cjkUnifiedIdeographs.get(i);
            }
        }

        this.shortChineseMessageBody = RandomStringUtils.random(MAXIMUM_PAYLOAD_SIZE / 8, chineseCharacters);
        this.longChineseMessageBody = RandomStringUtils.random(MAXIMUM_PAYLOAD_SIZE * 2, chineseCharacters);
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

    @Benchmark
    public String testShortChineseMessageBody() {
        this.apnsPayloadBuilder.setAlertBody(this.shortChineseMessageBody);
        return this.apnsPayloadBuilder.buildWithMaximumLength(MAXIMUM_PAYLOAD_SIZE);
    }

    @Benchmark
    public String testLongChineseMessageBody() {
        this.apnsPayloadBuilder.setAlertBody(this.longChineseMessageBody);
        return this.apnsPayloadBuilder.buildWithMaximumLength(MAXIMUM_PAYLOAD_SIZE);
    }
}
