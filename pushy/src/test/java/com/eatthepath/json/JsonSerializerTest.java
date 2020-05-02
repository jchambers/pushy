package com.eatthepath.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;


@SuppressWarnings("ZeroLengthArrayAllocation")
public class JsonSerializerTest {

    @Test
    void jsonSerializer() {
        assertThrows(InstantiationException.class, JsonSerializer::new);
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextMap")
    void writeJsonTextMap(final Map<?, ?> map, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(map, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextMap")
    void writeJsonTextAsStringMap(final Map<?, ?> map, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(map));
    }

    private static Stream<Arguments> parametersForWriteJsonTextMap() {
        final SortedMap<String, String> multiEntryMap;
        {
            multiEntryMap = new TreeMap<>();
            multiEntryMap.put("a", "b");
            multiEntryMap.put("c", "d");
        }

        return Stream.of(
                arguments(null, "null"),
                arguments(Collections.emptyMap(), "{}"),
                arguments(Collections.singletonMap("key", "value"), "{\"key\":\"value\"}"),
                arguments(multiEntryMap, "{\"a\":\"b\",\"c\":\"d\"}"),
                arguments(Collections.singletonMap("outerKey", Collections.singletonMap("innerKey", "value")),
                        "{\"outerKey\":{\"innerKey\":\"value\"}}")
        );
    }

    @Test
    void writeJsonTextMapNullKey() {
        final Map<?, ?> map = Collections.singletonMap(null, "test");

        assertThrows(NullPointerException.class, () -> JsonSerializer.writeJsonValue(map, new StringBuilder()));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextCollection")
    void writeJsonTextCollection(final Collection<?> collection, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(collection, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextCollection")
    void writeJsonTextAsStringCollection(final Collection<?> collection, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(collection));
    }

    private static Stream<Arguments> parametersForWriteJsonTextCollection() {
        return Stream.of(
                arguments(null, "null"),
                arguments(Collections.emptySet(), "[]"),
                arguments(Collections.singletonList(true), "[true]"),
                arguments(Arrays.asList(true, 4, "test"), "[true,4,\"test\"]"),
                arguments(Arrays.asList(Collections.singletonList(1), Collections.singletonList(2)), "[[1],[2]]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextByteArray")
    void writeJsonTextByteArray(final byte[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextByteArray")
    void writeJsonTextAsStringByteArray(final byte[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextByteArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new byte[0], "[]"),
                arguments(new byte[] { 1 }, "[1]"),
                arguments(new byte[] { 1, 2, 3 }, "[1,2,3]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextShortArray")
    void writeJsonTextShortArray(final short[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextShortArray")
    void writeJsonTextAsStringShortArray(final short[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextShortArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new short[0], "[]"),
                arguments(new short[] { 1 }, "[1]"),
                arguments(new short[] { 1, 2, 3 }, "[1,2,3]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextIntArray")
    void writeJsonTextIntArray(final int[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextIntArray")
    void writeJsonTextAsStringIntArray(final int[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextIntArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new int[0], "[]"),
                arguments(new int[] { 1 }, "[1]"),
                arguments(new int[] { 1, 2, 3 }, "[1,2,3]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextLongArray")
    void writeJsonTextLongArray(final long[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextLongArray")
    void writeJsonTextAsStringLongArray(final long[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextLongArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new long[0], "[]"),
                arguments(new long[] { 1L }, "[1]"),
                arguments(new long[] { 1L, 2L, 3L }, "[1,2,3]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextFloatArray")
    void writeJsonTextFloatArray(final float[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextFloatArray")
    void writeJsonTextAsStringFloatArray(final float[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextFloatArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new float[0], "[]"),
                arguments(new float[] { 1f }, "[1.0]"),
                arguments(new float[] { 1f, 2f, 3f }, "[1.0,2.0,3.0]"));

        // TODO What about NaN, Infinity in lists?
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextDoubleArray")
    void writeJsonTextDoubleArray(final double[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextDoubleArray")
    void writeJsonTextAsStringDoubleArray(final double[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextDoubleArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new double[0], "[]"),
                arguments(new double[] { 1d }, "[1.0]"),
                arguments(new double[] { 1d, 2d, 3d }, "[1.0,2.0,3.0]"));

        // TODO What about NaN, Infinity in lists?
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextBooleanArray")
    void writeJsonTextBooleanArray(final boolean[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextBooleanArray")
    void writeJsonTextAsStringBooleanArray(final boolean[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextBooleanArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new boolean[0], "[]"),
                arguments(new boolean[] { true }, "[true]"),
                arguments(new boolean[] { false, true }, "[false,true]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextCharArray")
    void writeJsonTextCharArray(final char[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextCharArray")
    void writeJsonTextAsStringCharArray(final char[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextCharArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new char[0], "[]"),
                arguments(new char[] { 'a' }, "[\"a\"]"),
                arguments(new char[] { 'a', '\t', '…' }, "[\"a\",\"\\t\",\"…\"]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextObjectArray")
    void writeJsonTextObjectArray(final Object[] array, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(array, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonTextObjectArray")
    void writeJsonTextAsStringObjectArray(final Object[] array, final String expectedValue) {
        assertEquals(expectedValue, JsonSerializer.writeJsonTextAsString(array));
    }

    private static Stream<Arguments> parametersForWriteJsonTextObjectArray() {
        return Stream.of(
                arguments(null, "null"),
                arguments(new Object[0], "[]"),
                arguments(new Object[] { Collections.emptyMap() }, "[{}]"),
                arguments(new Object[] { 1, true, "three" }, "[1,true,\"three\"]"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueObject")
    void writeJsonValueObject(final Object object, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(object, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueObject() {
        return Stream.of(
                arguments(null, "null"),
                arguments(Optional.empty(), "null"),
                arguments(Optional.of(false), "false"),
                arguments(Collections.emptyMap(), "{}"),
                arguments(Collections.emptySet(), "[]"),
                arguments(new byte[] { 1, 2, 3 }, "[1,2,3]"),
                arguments(new short[] { 1, 2, 3 }, "[1,2,3]"),
                arguments(new int[] { 1, 2, 3 }, "[1,2,3]"),
                arguments(new long[] { 1, 2, 3 }, "[1,2,3]"),
                arguments(new float[] { 1, 2, 3 }, "[1.0,2.0,3.0]"),
                arguments(new double[] { 1, 2, 3 }, "[1.0,2.0,3.0]"),
                arguments(new boolean[] { true, false }, "[true,false]"),
                arguments(new char[] { 'a', 'b', '\t' }, "[\"a\",\"b\",\"\\t\"]"),
                arguments(new Object[] { null, Collections.emptyMap(), 4, "test" }, "[null,{},4,\"test\"]"),
                arguments(4, "4"),
                arguments(true, "true"),
                arguments(new Object() {
                    @Override
                    public String toString() {
                        return "Other object";
                    }
                }, "\"Other object\""));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueString")
    void writeJsonValueString(final String string, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(string, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueString() {
        return Stream.of(
                arguments(null, "null"),
                arguments("", "\"\""),
                arguments("\r\n", "\"\\r\\n\""),
                arguments("\ta", "\"\\ta\""),
                arguments("🤘", "\"🤘\""),
                arguments("String with no escaped characters", "\"String with no escaped characters\""),
                arguments("String with \b\f\n\r\t lots/of \\ \"escaped characters\" \u0006 and still more text…",
                        "\"String with \\b\\f\\n\\r\\t lots\\/of \\\\ \\\"escaped characters\\\" \\u0006 and still more text…\""));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueChar")
    void writeJsonValueChar(final char c, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(c, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueChar() {
        return Stream.of(
                arguments('x', "\"x\""),
                arguments('"', "\"\\\"\""),
                arguments('\\', "\"\\\\\""),
                arguments('/', "\"\\/\""),
                arguments('\b', "\"\\b\""),
                arguments('\f', "\"\\f\""),
                arguments('\n', "\"\\n\""),
                arguments('\r', "\"\\r\""),
                arguments('\t', "\"\\t\""),
                arguments('\u0006', "\"\\u0006\""),
                arguments('√', "\"√\""));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueNumber")
    void writeJsonValueNumber(final Number number, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(number, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueNumber() {
        return Stream.of(
                arguments(null, "null"),
                arguments(1L, "1"),
                arguments(2, "2"),
                arguments((short) 3, "3"),
                arguments((byte) 4, "4"),
                arguments(5.5f, "5.5"),
                arguments(6.6d, "6.6")
        );
    }

    @Test
    void writeJsonValueNumberIllegalValues() {
        final StringBuilder stringBuilder = new StringBuilder();

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Float.valueOf(Float.NaN), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Float.valueOf(Float.NEGATIVE_INFINITY), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Float.valueOf(Float.POSITIVE_INFINITY), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.valueOf(Double.NaN), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.valueOf(Double.NEGATIVE_INFINITY), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.valueOf(Double.POSITIVE_INFINITY), stringBuilder));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueByte")
    void writeJsonValueByte(final byte b, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(b, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueByte() {
        return Stream.of(
                arguments((byte) 0, "0"),
                arguments((byte) 1, "1"),
                arguments((byte) -1, "-1"),
                arguments(Byte.MAX_VALUE, Byte.toString(Byte.MAX_VALUE)),
                arguments(Byte.MIN_VALUE, Byte.toString(Byte.MIN_VALUE)));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueShort")
    void writeJsonValueShort(final short s, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(s, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueShort() {
        return Stream.of(
                arguments((short) 0, "0"),
                arguments((short) 1L, "1"),
                arguments((short) -1L, "-1"),
                arguments(Short.MAX_VALUE, Short.toString(Short.MAX_VALUE)),
                arguments(Short.MIN_VALUE, Short.toString(Short.MIN_VALUE)));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueInt")
    void writeJsonValueInt(final int i, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(i, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueInt() {
        return Stream.of(
                arguments(0, "0"),
                arguments(1, "1"),
                arguments(-1, "-1"),
                arguments(Integer.MAX_VALUE, Integer.toString(Integer.MAX_VALUE)),
                arguments(Integer.MIN_VALUE, Integer.toString(Integer.MIN_VALUE)));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueLong")
    void writeJsonValueLong(final long l, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(l, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueLong() {
        return Stream.of(
                arguments(0L, "0"),
                arguments(1L, "1"),
                arguments(-1L, "-1"),
                arguments(Long.MAX_VALUE, Long.toString(Long.MAX_VALUE)),
                arguments(Long.MIN_VALUE, Long.toString(Long.MIN_VALUE)));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueFloatBoxed")
    void writeJsonValueFloatBoxed(final Float f, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(f, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueFloatBoxed() {
        return Stream.of(
                arguments(null, "null"),
                arguments(0f, "0.0"),
                arguments(1f, "1.0"),
                arguments(-1f, "-1.0"),
                arguments(1.2f, "1.2"),
                arguments(Float.MAX_VALUE, Float.toString(Float.MAX_VALUE)),
                arguments(Float.MIN_VALUE, Float.toString(Float.MIN_VALUE)));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueFloat")
    void writeJsonValueFloat(final float f, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(f, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueFloat() {
        return Stream.of(
                arguments(0f, "0.0"),
                arguments(1f, "1.0"),
                arguments(-1f, "-1.0"),
                arguments(1.2f, "1.2"),
                arguments(Float.MAX_VALUE, Float.toString(Float.MAX_VALUE)),
                arguments(Float.MIN_VALUE, Float.toString(Float.MIN_VALUE)));
    }

    @Test
    void writeJsonValueFloatIllegalValues() {
        final StringBuilder stringBuilder = new StringBuilder();

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Float.NaN, stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Float.NEGATIVE_INFINITY, stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Float.POSITIVE_INFINITY, stringBuilder));
    }

    @Test
    void writeJsonValueDoubleBoxedIllegalValues() {
        final StringBuilder stringBuilder = new StringBuilder();

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.valueOf(Double.NaN), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.valueOf(Double.NEGATIVE_INFINITY), stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.valueOf(Double.POSITIVE_INFINITY), stringBuilder));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueDouble")
    void writeJsonValueDouble(final double d, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(d, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueDouble() {
        return Stream.of(
                arguments(0d, "0.0"),
                arguments(1d, "1.0"),
                arguments(-1d, "-1.0"),
                arguments(1.2d, "1.2"),
                arguments(Double.MAX_VALUE, Double.toString(Double.MAX_VALUE)),
                arguments(Double.MIN_VALUE, Double.toString(Double.MIN_VALUE)));
    }

    @Test
    void writeJsonValueDoubleIllegalValues() {
        final StringBuilder stringBuilder = new StringBuilder();

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.NaN, stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.NEGATIVE_INFINITY, stringBuilder));

        assertThrows(IllegalArgumentException.class,
                () -> JsonSerializer.writeJsonValue(Double.POSITIVE_INFINITY, stringBuilder));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueBooleanBoxed")
    void writeJsonValueBooleanBoxed(final Boolean b, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(b, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueBooleanBoxed() {
        return Stream.of(
                arguments(null, "null"),
                arguments(Boolean.TRUE, "true"),
                arguments(Boolean.FALSE, "false"));
    }

    @ParameterizedTest
    @MethodSource("parametersForWriteJsonValueBoolean")
    void writeJsonValueBoolean(final boolean b, final String expectedValue) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        JsonSerializer.writeJsonValue(b, stringBuilder);

        assertEquals(expectedValue, stringBuilder.toString());
    }

    private static Stream<Arguments> parametersForWriteJsonValueBoolean() {
        return Stream.of(
                arguments(true, "true"),
                arguments(false, "false"));
    }
}
