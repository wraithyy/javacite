package io.github.wraithyy.javacite.core.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Type-checked access to snakeyaml-engine output; internal to javacite-core. */
public final class YamlNodes {

    private YamlNodes() {}

    public static Map<String, Object> map(Object node, String path) {
        if (!(node instanceof Map<?, ?> raw)) {
            throw new ConfigException("Expected a mapping at '" + path + "' but found " + describe(node));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    public static void checkKeys(Map<String, ?> map, Collection<String> valid, String path) {
        for (String key : map.keySet()) {
            if (!valid.contains(key)) {
                throw new ConfigException(
                        "Unknown key '" + key + "' at '" + path + "'. Valid keys: " + List.copyOf(valid));
            }
        }
    }

    public static List<String> strings(Object node, String path) {
        if (!(node instanceof List<?> raw)) {
            throw new ConfigException("Expected a list at '" + path + "' but found " + describe(node));
        }
        List<String> out = new ArrayList<>();
        for (Object item : raw) {
            if (!(item instanceof String s)) {
                throw new ConfigException("Expected a list of strings at '" + path + "' but found " + describe(item));
            }
            out.add(s);
        }
        return out;
    }

    public static String string(Object node, String path) {
        if (node instanceof String s) {
            return s;
        }
        throw new ConfigException("Expected a string at '" + path + "' but found " + describe(node));
    }

    public static int integer(Object node, String path) {
        if (node instanceof Integer i) {
            return i;
        }
        throw new ConfigException("Expected an integer at '" + path + "' but found " + describe(node));
    }

    public static double number(Object node, String path) {
        if (node instanceof Number n) {
            return n.doubleValue();
        }
        throw new ConfigException("Expected a number at '" + path + "' but found " + describe(node));
    }

    /** Matches the scalar against the keys of an enum's constants; booleans map to on/off. */
    public static <E extends Enum<E>> E enumValue(Class<E> type, Function<E, String> key, Object node, String path) {
        Object scalar = node instanceof Boolean b ? (b ? "on" : "off") : node;
        List<String> valid =
                java.util.Arrays.stream(type.getEnumConstants()).map(key).toList();
        if (scalar instanceof String s) {
            for (E e : type.getEnumConstants()) {
                if (key.apply(e).equals(s)) {
                    return e;
                }
            }
        }
        throw new ConfigException(
                "Invalid value " + describe(node) + " at '" + path + "'. Valid values: " + valid);
    }

    private static String describe(Object node) {
        return node == null ? "null" : "'" + node + "'";
    }
}
