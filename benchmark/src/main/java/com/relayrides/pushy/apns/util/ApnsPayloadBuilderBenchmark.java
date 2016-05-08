package com.relayrides.pushy.apns.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class ApnsPayloadBuilderBenchmark {

    private ApnsPayloadBuilder apnsPayloadBuilder;

    private static final int MAXIMUM_PAYLOAD_SIZE = 4096;

    @Param({"512", "8192"})
    public int messageBodyLength;

    @Param({"BASIC_LATIN", "CJK_UNIFIED_IDEOGRAPHS"})
    public String unicodeBlockName;

    private String messageBody;

    @Setup
    public void setUp() {
        this.apnsPayloadBuilder = new ApnsPayloadBuilder();

        final char[] messageBodyCharacters;
        {
            final Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.forName(this.unicodeBlockName);
            final List<Character> charactersInBlock = new ArrayList<>();

            for (int codePoint = Character.MIN_CODE_POINT; codePoint < Character.MAX_CODE_POINT; codePoint++) {
                if (unicodeBlock.equals(Character.UnicodeBlock.of(codePoint)) && !Character.isISOControl(codePoint)) {
                    charactersInBlock.add((char) codePoint);
                }
            }

            messageBodyCharacters = new char[charactersInBlock.size()];

            for (int i = 0; i < charactersInBlock.size(); i++) {
                messageBodyCharacters[i] = charactersInBlock.get(i);
            }
        }

        this.messageBody = RandomStringUtils.random(this.messageBodyLength, messageBodyCharacters);
    }

    @Benchmark
    public String testBuildWithMaximumLength() {
        this.apnsPayloadBuilder.setAlertBody(this.messageBody);
        return this.apnsPayloadBuilder.buildWithMaximumLength(MAXIMUM_PAYLOAD_SIZE);
    }
}
