package com.eatthepath.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonParserTest {

    @Test
    void parseAsObject() throws ParseException {
        final JsonParser parser = new JsonParser();

        assertThrows(ParseException.class, () -> parser.parseJsonObject(""));
        assertThrows(ParseException.class, () -> parser.parseJsonObject("[]"));

        assertEquals(Collections.singletonMap("moose", true),
                parser.parseJsonObject("  \t\r\n{   \"moose\"\t: true    }\n\n"));
    }

    @ParameterizedTest
    @MethodSource("argumentsForParseNextValue")
    void parseNextValue(final String json, final Object expectedValue) throws ParseException {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertEquals(expectedValue, parser.parseNextValue());
    }

    private static Stream<Arguments> argumentsForParseNextValue() {
        return Stream.of(
                arguments("true", true),
                arguments("false", false),
                arguments("null", null),
                arguments(" true", true),
                arguments("\tfalse", false),
                arguments("\r\nnull", null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"not value json", "()", "+", "fun"})
    void parseNextValueBadToken(final String json) {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertThrows(ParseException.class, parser::parseNextValue);
    }

    @ParameterizedTest
    @MethodSource("argumentsForParseString")
    void parseString(final String json, final String expectedString) throws ParseException {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertEquals(expectedString, parser.parseString());
    }

    private static Stream<Arguments> argumentsForParseString() {
        return Stream.of(
                arguments("\"\"", ""),
                arguments("\"test\"", "test"),
                arguments("\"\\t\"", "\t"),
                arguments("\"Line\\nbreak\"", "Line\nbreak"),
                arguments("\"A string with \\\"quotes.\\\"\"", "A string with \"quotes.\""),
                arguments("\"\\u2713\"", "✓"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"", "\"Oh no \\", "\"Bad unicode \\u123" })
    void parseStringBadValue(final String json) {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertThrows(ParseException.class, parser::parseString);
    }

    @ParameterizedTest
    @MethodSource("argumentsForParseNumber")
    void parseNumber(final String json, final Number expectedNumber) {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        final Number number = parser.parseNumber();

        assertEquals(expectedNumber, number);
    }

    private static Stream<Arguments> argumentsForParseNumber() {
        return Stream.of(
                arguments("0", 0L),
                arguments("-1", -1L),
                arguments("1234567890", 1234567890L),
                arguments("1.5", 1.5),
                arguments("-1.5", -1.5),
                arguments("2e3", 2e3),
                arguments("-2e-3", -2e-3));
    }

    @ParameterizedTest
    @MethodSource("argumentsForParseObject")
    void parseObject(final String json, final Map<String, Object> expectedMap) throws ParseException {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertEquals(expectedMap, parser.parseObject());
    }

    private static Stream<Arguments> argumentsForParseObject() {
        final Map<String, Object> multipleEntryMap = new HashMap<>();
        multipleEntryMap.put("moose", 12L);
        multipleEntryMap.put("porcupine", true);

        final Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("a", Collections.emptyMap());
        nestedMap.put("b", Collections.emptyList());
        nestedMap.put("c", false);

        return Stream.of(
                arguments("{}", Collections.emptyMap()),
                arguments("{\"moose\": 12}", Collections.singletonMap("moose", 12L)),
                arguments("{\"moose\": 12, \"porcupine\": true}", multipleEntryMap),
                arguments("{\"moose\": {\"porcupine\": true}}",
                        Collections.singletonMap("moose", Collections.singletonMap("porcupine", true))),
                arguments("{\"a\": {}, \"b\": [], \"c\": false}", nestedMap));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"key\"", "{\"key\":", "{\"key\": 12", "{false: true}"})
    void parseObjectBadValue(final String json) {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertThrows(ParseException.class, parser::parseObject);
    }

    @ParameterizedTest
    @MethodSource("argumentsForParseList")
    void parseList(final String json, final List<Object> expectedList) throws ParseException {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertEquals(expectedList, parser.parseList());
    }

    private static Stream<Arguments> argumentsForParseList() {
        return Stream.of(
                arguments("[]", Collections.emptyList()),
                arguments("[ ]", Collections.emptyList()),
                arguments("[true]", Collections.singletonList(true)),
                arguments("[true, false, true]", Arrays.asList(true, false, true)),
                arguments("[[], {}, false]", Arrays.asList(Collections.emptyList(), Collections.emptyMap(), false)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[", "[,", "[12", "[12,"})
    void parseListBadValue(final String json) {
        final JsonParser parser = new JsonParser();
        parser.setJsonString(json);

        assertThrows(ParseException.class, parser::parseList);
    }

    @ParameterizedTest
    @MethodSource("argumentsForFindNextToken")
    void findNextToken(final String string, final int start, final int expectedTokenIndex) throws ParseException {
        assertEquals(expectedTokenIndex, JsonParser.findNextToken(string, start));
    }

    private static Stream<Arguments> argumentsForFindNextToken() {
        return Stream.of(
                arguments("test", 0, 0),
                arguments(" {}", 0, 1),
                arguments(" {}", 1, 1));
    }

    @ParameterizedTest
    @MethodSource("argumentsForFindNextTokenEndOfString")
    void findNextTokenEndOfString(final String string, final int start) {
        assertThrows(ParseException.class, () -> JsonParser.findNextToken(string, start));
    }

    private static Stream<Arguments> argumentsForFindNextTokenEndOfString() {
        return Stream.of(
                arguments("", 0),
                arguments("", 1),
                arguments("test", 5));
    }
}
