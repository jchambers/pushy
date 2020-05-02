package com.eatthepath.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class JsonDeserializerTest {

    @ParameterizedTest
    @MethodSource("argumentsForParseJsonObject")
    void parseJsonObject(final String jsonString, final Map<String, Object> expectedObject) throws ParseException {
        assertEquals(expectedObject, new JsonDeserializer().parseJsonObject(jsonString));
    }

    private static Stream<Arguments> argumentsForParseJsonObject() {
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
    @ValueSource(strings = {"", "{", "false", "{{}"})
    void parseJsonObjectBogusJsonString(final String jsonString) {
        assertThrows(ParseException.class, () -> new JsonDeserializer().parseJsonObject(jsonString));
    }

    @Test
    void parseJsonObjectReuse() throws ParseException {
        final JsonDeserializer jsonDeserializer = new JsonDeserializer();

        assertEquals(jsonDeserializer.parseJsonObject("{\"moose\": 12}"),
                Collections.singletonMap("moose", 12L));

        assertEquals(jsonDeserializer.parseJsonObject("{\"moose\": {\"porcupine\": true}}"),
                Collections.singletonMap("moose", Collections.singletonMap("porcupine", true)));
    }
}
