package com.eatthepath.json;

import java.io.StringReader;
import java.util.Map;

/**
 * <p>An extremely simple JSON object deserializer that interprets JSON objects as Java primitives. JSON types are
 * mapped to Java types as follows:</p>
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
 *             <td>{@link java.util.List}&lt;{@link Object}&gt;</td>
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
 * <p>{@code JsonDeserializer} instances are <em>not</em> thread-safe; only one thread may safely use a single
 * deserializer at a time.</p>
 *
 * @author <a href="https://github.com/jchambers">Jon Chambers</a>
 *
 * @since 0.14.0
 */
public class JsonDeserializer {

    private final JsonParser jsonParser = new JsonParser(new StringReader(""));

    /**
     * Parses a JSON string as a JSON object.
     *
     * @param jsonString the JSON string to be parsed
     *
     * @return a Java representation of the JSON-encoded object
     *
     * @throws ParseException if the given {@code jsonString} does not represent a valid JSON object
     */
    public Map<String, Object> parseJsonObject(final String jsonString) throws ParseException {
        jsonParser.ReInit(new StringReader(jsonString));
        return jsonParser.object();
    }
}
