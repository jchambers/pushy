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

package com.turo.pushy.apns.util;

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
