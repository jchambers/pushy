package com.eatthepath.json;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.StringReader;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonParserTest {

    @ParameterizedTest
    @MethodSource("argumentsForObject")
    void object(final String jsonString, final Map<String, Object> expectedMap) throws ParseException {
        assertEquals(expectedMap, new JsonParser(new StringReader(jsonString)).object());
    }

    private static Stream<Arguments> argumentsForObject() {
        final Map<String, Object> multipleEntryMap = new HashMap<>();
        multipleEntryMap.put("moose", 12L);
        multipleEntryMap.put("porcupine", true);

        return Stream.of(
                arguments("{}", Collections.emptyMap()),
                arguments("{\"moose\": 12}", Collections.singletonMap("moose", 12L)),
                arguments("{\"moose\": 12, \"porcupine\": true}", multipleEntryMap),
                arguments("{\"moose\": {\"porcupine\": true}}",
                        Collections.singletonMap("moose", Collections.singletonMap("porcupine", true))));
    }

    @ParameterizedTest
    @MethodSource("argumentsForList")
    void list(final String jsonString, final List<Object> expectedList) throws ParseException {
        assertEquals(expectedList, new JsonParser(new StringReader(jsonString)).list());
    }

    private static Stream<Arguments> argumentsForList() {
        return Stream.of(
                arguments("[]", Collections.emptyList()),
                arguments("[true]", Collections.singletonList(true)),
                arguments("[true, \"electrode\"]", Arrays.asList(true, "electrode")));
    }

    @ParameterizedTest
    @MethodSource("argumentsForValue")
    void value(final String jsonString, final Object expectedValue) throws ParseException {
        assertEquals(expectedValue, new JsonParser(new StringReader(jsonString)).value());
    }

    private static Stream<Arguments> argumentsForValue() {
        return Stream.of(
                arguments("\"test\"", "test"),
                arguments("17", 17L),
                arguments("{\"test\": \"moose\"}", Collections.singletonMap("test", "moose")),
                arguments("[7, false, null]", Arrays.asList(7L, false, null)),
                arguments("true", true),
                arguments("false", false),
                arguments("null", null));
    }

    @ParameterizedTest
    @MethodSource("argumentsForString")
    void string(final String jsonString, final String expectedString) throws ParseException {
        assertEquals(expectedString, new JsonParser(new StringReader(jsonString)).string());
    }

    private static Stream<Arguments> argumentsForString() {
        return Stream.of(
                arguments("\"\"", ""),
                arguments("\"test\"", "test"),
                arguments("\"\\t\"", "\t"),
                arguments("\"Line\\nbreak\"", "Line\nbreak"),
                arguments("\"\\u2713\"", "✓"));
    }

    @ParameterizedTest
    @MethodSource("argumentsForNumber")
    void number(final String jsonString, final Number expectedNumber) throws ParseException {
        assertEquals(expectedNumber, new JsonParser(new StringReader(jsonString)).number());
    }

    private static Stream<Arguments> argumentsForNumber() {
        return Stream.of(
                arguments("0", 0L),
                arguments("1", 1L),
                arguments("1234567890", 1234567890L),
                arguments("-1234567890", -1234567890L),
                arguments("1.23", 1.23),
                arguments("-1.23", -1.23),
                arguments("-2e4", -2e4),
                arguments("7.2e-3", 7.2e-3));
    }
}
