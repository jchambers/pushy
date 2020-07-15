package com.eatthepath.json;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>An extremely simple JSON parser that interprets JSON objects as Java primitives. JSON types are mapped to Java
 * types as follows:</p>
 *
 * <table>
 *     <caption>Mapping of JSON types to Java types</caption>
 *
 *     <thead>
 *         <tr>
 *             <th>JSON type</th>
 *             <th>Java type</th>
 *         </tr>
 *     </thead>
 *
 *     <tbody>
 *         <tr>
 *             <td>string</td>
 *             <td>{@link String}</td>
 *         </tr>
 *
 *         <tr>
 *             <td>number</td>
 *             <td>{@link Number} ({@link Long} or {@link Double})</td>
 *         </tr>
 *
 *         <tr>
 *             <td>object</td>
 *             <td>{@link Map}&lt;{@link String}, {@link Object}&gt;</td>
 *         </tr>
 *
 *         <tr>
 *             <td>array</td>
 *             <td>{@link List}&lt;{@link Object}&gt;</td>
 *         </tr>
 *
 *         <tr>
 *             <td>boolean</td>
 *             <td>boolean</td>
 *         </tr>
 *
 *         <tr>
 *             <td>null</td>
 *             <td>null</td>
 *         </tr>
 *     </tbody>
 * </table>
 *
 * <p>{@code JsonParser} instances are <em>not</em> thread-safe; only one thread may safely use a single parser at a
 * time, but parsers can be reused (i.e. callers do not need to construct a new parser for each parsing operation).</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.14.1
 */
public class JsonParser {

    private transient String jsonString;
    private transient int position;

    private transient final StringBuilder stringBuilder = new StringBuilder();

    /**
     * Constructs a new JSON parser. Parsers are not thread-safe, but may be reused.
     */
    public JsonParser() {}

    /**
     * Parses the given JSON string as a JSON object.
     *
     * @param jsonString the JSON string to parse
     *
     * @return a Map representing the JSON object
     *
     * @throws ParseException if the given JSON string could not be parsed as a JSON object for any reason
     */
    public Map<String, Object> parseJsonObject(final String jsonString) throws ParseException {
        this.jsonString = jsonString;
        this.position = findNextToken(jsonString, 0);

        if (jsonString.charAt(position) != '{') {
            throw new ParseException("JSON string does not represent a JSON object.", position);
        }

        return parseObject();
    }

    /**
     * Parses and returns the JSON value (which may be of any JSON type) at the current read position and advances the
     * read position to the character after the end of the value.
     *
     * @return the value at the current read position
     */
    Object parseNextValue() throws ParseException {
        position = findNextToken(jsonString, position);

        final char c = jsonString.charAt(position);
        final Object value;

        if (c == '"') {
            value = parseString();
        } else if (c == '-' || (c >= '0' && c <= '9')) {
            final int numberStart = position;

            try {
                value = parseNumber();
            } catch (final NumberFormatException e) {
                throw new ParseException("Could not parse number", numberStart);
            }
        } else if (c == '{') {
            value = parseObject();
        } else if (c == '[') {
            value = parseList();
        } else if (c == 't') {
            if (jsonString.regionMatches(position, "true", 0, 4)) {
                value = true;
                position += 4;
            } else {
                throw new ParseException("Unexpected literal", position);
            }
        } else if (c == 'f') {
            if (jsonString.regionMatches(position, "false", 0, 5)) {
                value = false;
                position += 5;
            } else {
                throw new ParseException("Unexpected literal", position);
            }
        } else if (c == 'n') {
            if (jsonString.regionMatches(position, "null", 0, 4)) {
                value = null;
                position += 4;
            } else {
                throw new ParseException("Unexpected literal", position);
            }
        } else {
            throw new ParseException("Unexpected token", position);
        }

        return value;
    }

    /**
     * Parses and returns the string at the current read position and advances the read position to the character after
     * the end of the string.
     *
     * @return the string at the current read position
     */
    String parseString() throws ParseException {
        // Reset and reuse the string builder to avoid unnecessary allocations
        stringBuilder.setLength(0);

        // Skip the opening quote
        position += 1;
        
        int start = position;

        for (; position < jsonString.length(); position++) {
            final char c = jsonString.charAt(position);

            if (c == '\\') {
                final int charsConsumed;
                final char escapedChar;

                if (position + 1 >= jsonString.length()) {
                    throw new ParseException("Expected escaped character, but reached end of input", position);
                }

                switch (jsonString.charAt(position + 1)) {
                    case '\"': {
                        charsConsumed = 1;
                        escapedChar = '\"';
                        break;
                    }

                    case '\\': {
                        charsConsumed = 1;
                        escapedChar = '\\';
                        break;
                    }

                    case '/': {
                        charsConsumed = 1;
                        escapedChar = '/';
                        break;
                    }

                    case 'b': {
                        charsConsumed = 1;
                        escapedChar = '\b';
                        break;
                    }

                    case 'f': {
                        charsConsumed = 1;
                        escapedChar = '\f';
                        break;
                    }

                    case 'n': {
                        charsConsumed = 1;
                        escapedChar = '\n';
                        break;
                    }

                    case 'r': {
                        charsConsumed = 1;
                        escapedChar = '\r';
                        break;
                    }

                    case 't': {
                        charsConsumed = 1;
                        escapedChar = '\t';
                        break;
                    }

                    case 'u': {
                        if (position + 5 >= jsonString.length()) {
                            throw new ParseException("Expected escaped unicode sequence, but reached end of input", position);
                        }

                        charsConsumed = 5;
                        escapedChar = (char) Integer.parseInt(jsonString.substring(position + 2, position + 6), 16);
                        break;
                    }

                    default: {
                        throw new ParseException("Illegal escaped character: " + jsonString.charAt(position + 1), position);
                    }
                }

                stringBuilder.append(jsonString, start, position);
                stringBuilder.append(escapedChar);

                position += charsConsumed;
                start = position + 1;
            } else if (c == '"') {
                // If the string builder is empty, we didn't wind up un-escaping anything and can just return a
                // substring to save an array copy (original string -> returned string as opposed to original string ->
                // string builder -> returned string).
                final String unescapedString;

                if (stringBuilder.length() == 0) {
                    unescapedString = jsonString.substring(start, position);
                } else {
                    stringBuilder.append(jsonString, start, position);
                    unescapedString = stringBuilder.toString();
                }

                // Position the read index after the end of the string
                position += 1;

                return unescapedString;
            }
        }

        throw new ParseException("Expected \", but reached end of input.", position);
    }

    /**
     * Parses and returns the number at the current read position and advances the read position to the character after
     * the end of the number token.
     *
     * @return the number at the current read position
     */
    Number parseNumber() {
        final boolean negate = jsonString.charAt(position) == '-';
        boolean parseAsDouble = false;

        final int startInclusive = position;
        int endExclusive;

        long parsedLong = 0;

        for (endExclusive = negate ? position + 1 : position; endExclusive < jsonString.length(); endExclusive++) {
            final char c = jsonString.charAt(endExclusive);

            if (c >= '0' && c <= '9') {
                parsedLong *= 10;
                parsedLong += c - '0';
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || (c == '-')) {
                parseAsDouble = true;
            } else {
                break;
            }
        }

        position = endExclusive;

        if (parseAsDouble) {
            return Double.parseDouble(jsonString.substring(startInclusive, endExclusive));
        } else {
            return negate ? -parsedLong : parsedLong;
        }
    }

    /**
     * Parses and returns the object at the current read position and advances the read position to the character after
     * the end of the object.
     *
     * @return the object at the current read position
     */
    Map<String, Object> parseObject() throws ParseException {
        // Advance past the opening bracket
        position = findNextToken(jsonString, position + 1);

        final Map<String, Object> map = new HashMap<>();

        while (jsonString.charAt(position) != '}') {
            if (jsonString.charAt(position) != '"') {
                throw new ParseException("Expected a string", position);
            }

            final String key = parseString();

            position = findNextToken(jsonString, position);

            if (jsonString.charAt(position) != ':') {
                throw new ParseException("Expected ':'", position);
            }

            // Advance past the key/value separator
            position += 1;

            map.put(key, parseNextValue());

            position = findNextToken(jsonString, position);

            if (jsonString.charAt(position) == ',') {
                // Position the cursor at the start of the token after the comma
                position = findNextToken(jsonString, position + 1);
            }
        }

        // Advance past the closing bracket
        position += 1;

        return map;
    }

    /**
     * Parses and returns the list at the current read position and advances the read position to the character after
     * the end of the list.
     *
     * @return the list at the current read position
     */
    List<Object> parseList() throws ParseException {
        // Advance past the opening bracket
        position = findNextToken(jsonString, position + 1);

        final List<Object> list = new ArrayList<>();

        while (jsonString.charAt(position) != ']') {
            list.add(parseNextValue());

            position = findNextToken(jsonString, position);

            if (jsonString.charAt(position) == ',') {
                // Position the cursor at the start of the token after the comma
                position = findNextToken(jsonString, position + 1);
            }
        }

        // Advance past the closing bracket
        position += 1;

        return list;
    }

    // Visible for testing
    void setJsonString(final String jsonString) {
        this.jsonString = jsonString;
        this.position = 0;
    }

    /**
     * Returns the index of the first character in the given string at or after the given start index that may be the
     * first character of a non-whitespace JSON token.
     *
     * @param string the string in which to find a token
     * @param start the first index at which to look for a non-whitespace token
     *
     * @return the index of the first non-whitespace character in the given string at or after the given start index
     *
     * @throws ParseException if the end of the string was reached before a non-whitespace token was found
     */
    static int findNextToken(final String string, final int start) throws ParseException {
        for (int i = start; i < string.length(); i++) {
            final char c = string.charAt(i);

            if (!(c == ' ' || c == '\t' || c == '\r' || c == '\n')) {
                return i;
            }
        }

        throw new ParseException("Expected token, but reached end of input", start);
    }
}
