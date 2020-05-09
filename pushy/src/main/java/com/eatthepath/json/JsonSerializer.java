package com.eatthepath.json;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * <p>A JSON serializer is responsible for producing complete and legal JSON texts (objects or arrays). It can serialize
 * a {@link Map} as a JSON object or any {@link Collection} or array as a JSON list. The keys of a {@code Map} are
 * always serialized as strings. Values within a {@code Map}, {@code Collection}, or array are converted to JSON values
 * as follows:</p>
 *
 * <table>
 *  <caption>Mapping of Java types to JSON types</caption>
 *  <thead>
 *      <tr>
 *          <th>Object type</th>
 *          <th>JSON representation</th>
 *      </tr>
 *  </thead>
 *  <tbody>
 *      <tr>
 *          <td>{@code null}</td>
 *          <td>{@code null}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@link Map}</td>
 *          <td>{@code object}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@link Collection}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code byte[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code short[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code int[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code long[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code float[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code double[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code boolean[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@code char[]}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@link Object}{@code []}</td>
 *          <td>{@code list}</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@link Number}</td>
 *          <td>{@code number} (or {@code null})</td>
 *      </tr>
 *
 *      <tr>
 *          <td>{@link Boolean}</td>
 *          <td>{@code true} or {@code false} (or {@code null})</td>
 *      </tr>
 *
 *      <tr>
 *          <td>all others</td>
 *          <td>the result of {@code obj.toString()}</td>
 *      </tr>
 *  </tbody>
 * </table>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @see <a href="http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf">ECMA-404 The JSON Data
 * Interchange Standard</a>
 */
public class JsonSerializer {

    /**
     * Don't allow direct instantiation.
     *
     * @throws InstantiationException in all cases
     */
    JsonSerializer() throws InstantiationException {
        throw new InstantiationException("JsonSerializer must not be instantiated directly.");
    }

    /**
     * Writes the given {@link Map} as a JSON object to the given {@link Appendable}. Note that the keys of the given
     * map will be represented as {@code Strings} (via their {@link Object#toString() toString} method) regardless of
     * their actual type.
     *
     * @param map the map to write as a JSON text
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final Map<?, ?> map, final Appendable out) throws IOException {
        if (map == null) {
            out.append("null");
        } else if (map.isEmpty()) {
            out.append("{}");
        } else {
            out.append('{');

            boolean valuesWritten = false;

            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw new NullPointerException("JSON object keys must not be null.");
                }

                if (valuesWritten) {
                    out.append(',');
                }

                writeJsonValue(entry.getKey().toString(), out);
                out.append(':');
                writeJsonValue(entry.getValue(), out);

                valuesWritten = true;
            }

            out.append('}');
        }
    }

    /**
     * Returns the given {@link Map} as a JSON string. Note that the keys of the given map will be represented as
     * {@code Strings} (via their {@link Object#toString() toString} method) regardless of their actual type.
     *
     * @param map the map to return as a JSON text
     *
     * @return a JSON string representing the given map
     */
    public static String writeJsonTextAsString(final Map<?, ?> map) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(map, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given {@link Collection} as a JSON list to the given {@link Appendable}.
     *
     * @param collection the collection to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final Collection<?> collection, final Appendable out) throws IOException {
        if (collection == null) {
            out.append("null");
        } else if (collection.isEmpty()) {
            out.append("[]");
        } else {
            out.append('[');

            boolean valuesWritten = false;

            for (final Object o : collection) {
                if (valuesWritten) {
                    out.append(',');
                }

                writeJsonValue(o, out);

                valuesWritten = true;
            }

            out.append(']');
        }
    }

    /**
     * Returns the given {@link Collection} as a JSON string.
     *
     * @param collection the collection to return as a JSON text
     *
     * @return a JSON string representing the given collection
     */
    public static String writeJsonTextAsString(final Collection<?> collection) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(collection, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code bytes} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final byte[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code bytes} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final byte[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code shorts} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final short[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code shorts} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final short[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code ints} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final int[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code ints} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final int[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code longs} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final long[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code longs} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final long[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code floats} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final float[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code floats} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final float[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code doubles} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final double[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code doubles} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final double[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@code booleans} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final boolean[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            out.append(String.valueOf(array[0]));

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                out.append(String.valueOf(array[i]));
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code booleans} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final boolean[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of characters as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final char[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            writeJsonValue(array[0], out);

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                writeJsonValue(array[i], out);
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code chars} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final char[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given array of {@link Object Objects} as a JSON list to the given {@link Appendable}.
     *
     * @param array the array to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON text
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    public static void writeJsonText(final Object[] array, final Appendable out) throws IOException {
        if (array == null) {
            out.append("null");
        } else if (array.length == 0) {
            out.append("[]");
        } else {
            out.append('[');
            writeJsonValue(array[0], out);

            for (int i = 1; i < array.length; i++) {
                out.append(',');
                writeJsonValue(array[i], out);
            }

            out.append(']');
        }
    }

    /**
     * Returns the given array of {@code Objects} as a JSON string.
     *
     * @param array the array to return as a JSON text
     *
     * @return a JSON string representing the given array
     */
    public static String writeJsonTextAsString(final Object[] array) {
        final StringBuilder stringBuilder = new StringBuilder();

        try {
            writeJsonText(array, stringBuilder);
            return stringBuilder.toString();
        } catch (final IOException e) {
            // This should never happen for a StringBuilder
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the given object as a JSON value.
     *
     * @param o the object to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final Object o, final Appendable out) throws IOException {
        if (o == null) {
            out.append("null");
        } else if (o instanceof Optional) {
            final Optional<?> optional = (Optional<?>) o;

            if (optional.isPresent()) {
                writeJsonValue(optional.get(), out);
            } else {
                writeJsonValue((Object) null, out);
            }
        } else if (o instanceof Map) {
            writeJsonText((Map<?, ?>) o, out);
        } else if (o instanceof Collection) {
            writeJsonText((Collection<?>) o, out);
        } else if (o instanceof byte[]) {
            writeJsonText((byte[]) o, out);
        } else if (o instanceof short[]) {
            writeJsonText((short[]) o, out);
        } else if (o instanceof int[]) {
            writeJsonText((int[]) o, out);
        } else if (o instanceof long[]) {
            writeJsonText((long[]) o, out);
        } else if (o instanceof float[]) {
            writeJsonText((float[]) o, out);
        } else if (o instanceof double[]) {
            writeJsonText((double[]) o, out);
        } else if (o instanceof boolean[]) {
            writeJsonText((boolean[]) o, out);
        } else if (o instanceof char[]) {
            writeJsonText((char[]) o, out);
        } else if (o instanceof Object[]) {
            writeJsonText((Object[]) o, out);
        } else if (o instanceof Number) {
            writeJsonValue((Number) o, out);
        } else if (o instanceof Boolean) {
            writeJsonValue((Boolean) o, out);
        } else {
            writeJsonValue(o.toString(), out);
        }
    }

    /**
     * Writes the given {@link String} as a JSON string.
     *
     * @param string the string to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final String string, final Appendable out) throws IOException {

        if (string == null) {
            out.append("null");
        } else if ("".equals(string)) {
            out.append("\"\"");
        } else {

            out.append('\"');

            int start = 0;

            // The strategy here is to make as few writes as possible by looking for substrings of characters that don't
            // need to be escaped. Whenever we hit a character that needs to be escaped, we write all of the
            // previously-unwritten characters up to that point, then add the escaped version of that character.
            for (int i = 0; i < string.length(); i++) {
                final char c = string.charAt(i);

                if (c == '"' || c == '\\' || c == '/' || c == '\b' || c == '\f' || c == '\n' || c == '\r' || c == '\t' ||
                        Character.isISOControl(c)) {

                    out.append(string, start, i);
                    appendEscapedCharacter(c, out);

                    start = i + 1;
                }
            }

            if (start == 0) {
                // We hit the end of the string without appending anything
                out.append(string);
            } else if (start < string.length()) {
                out.append(string, start, string.length());
            }

            out.append('\"');
        }
    }

    /**
     * Writes the given {@code char} as a JSON string.
     *
     * @param c the character to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final char c, final Appendable out) throws IOException {
        out.append('"');
        appendEscapedCharacter(c, out);
        out.append('"');
    }

    /**
     * Appends the given {@code char} to the given {@link Appendable}, escaping it if necessary.
     *
     * @param c the character to append
     * @param out the {@code Appendable} to which to write the given character
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    private static void appendEscapedCharacter(final char c, final Appendable out) throws IOException {
        switch (c) {
            case '"':
                out.append("\\\"");
                break;

            case '\\':
                out.append("\\\\");
                break;

            case '/':
                out.append("\\/");
                break;

            case '\b':
                out.append("\\b");
                break;

            case '\f':
                out.append("\\f");
                break;

            case '\n':
                out.append("\\n");
                break;

            case '\r':
                out.append("\\r");
                break;

            case '\t':
                out.append("\\t");
                break;

            default:
                if (Character.isISOControl(c)) {
                    out.append("\\u");
                    out.append(Integer.toHexString(0x10000 | c), 1, 5);
                } else {
                    out.append(c);
                }

                break;
        }
    }

    /**
     * Writes the given {@link Number} as a JSON number.
     *
     * @param number the number to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final Number number, final Appendable out) throws IOException {
        if (number == null) {
            out.append("null");
        } else if (number instanceof Float) {
            // Floats can have NaN or infinite values; pass them off to a dedicated handler
            writeJsonValue(number.floatValue(), out);
        } else if (number instanceof Double) {
            // Doubles can have NaN or infinite values; pass them off to a dedicated handler
            writeJsonValue(number.doubleValue(), out);
        } else {
            out.append(number.toString());
        }
    }

    /**
     * Writes the given {@code byte} as a JSON number.
     *
     * @param b the byte to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final byte b, final Appendable out) throws IOException {
        out.append(Byte.toString(b));
    }

    /**
     * Writes the given {@code short} as a JSON number.
     *
     * @param s the short to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final short s, final Appendable out) throws IOException {
        out.append(Short.toString(s));
    }

    /**
     * Writes the given {@code int} as a JSON number.
     *
     * @param i the integer to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final int i, final Appendable out) throws IOException {
        out.append(Integer.toString(i));
    }

    /**
     * Writes the given {@code long} as a JSON number.
     *
     * @param l the long to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final long l, final Appendable out) throws IOException {
        out.append(Long.toString(l));
    }

    /**
     * Writes the given {@code float} as a JSON number. Note that {@code NaN} values and non-finite numbers cannot be
     * written as JSON values.
     *
     * @param f the float to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     * @throws IllegalArgumentException if the given float was not a number or was not finite
     */
    static void writeJsonValue(final float f, final Appendable out) throws IOException {
        if (Float.isInfinite(f)) {
            throw new IllegalArgumentException("Cannot write non-finite numbers as JSON values.");
        }

        if (Float.isNaN(f)) {
            throw new IllegalArgumentException("Cannot write NaN as a JSON value.");
        }

        out.append(Float.toString(f));
    }

    /**
     * Writes the given {@code double} as a JSON number. Note that {@code NaN} values and non-finite numbers cannot be
     * written as JSON values.
     *
     * @param d the double to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     * @throws IllegalArgumentException if the given double was not a number or was not finite
     */
    static void writeJsonValue(final double d, final Appendable out) throws IOException {
        if (Double.isInfinite(d)) {
            throw new IllegalArgumentException("Cannot write non-finite numbers as JSON values.");
        }

        if (Double.isNaN(d)) {
            throw new IllegalArgumentException("Cannot write NaN as a JSON value.");
        }

        out.append(Double.toString(d));
    }

    /**
     * Writes the given {@link Boolean} as a JSON value.
     *
     * @param b the boolean to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final Boolean b, final Appendable out) throws IOException {
        if (b == null) {
            out.append("null");
        } else {
            writeJsonValue(b.booleanValue(), out);
        }
    }

    /**
     * Writes the given {@code boolean} as a JSON value.
     *
     * @param b the boolean to write as a JSON value
     * @param out the {@code Appendable} to which to write the JSON value
     *
     * @throws IOException in the event of an I/O error when writing to the given {@code Appendable}
     */
    static void writeJsonValue(final boolean b, final Appendable out) throws IOException {
        out.append(b ? "true" : "false");
    }
}
