package io.github.wraithyy.javacite.core.sonar;

import java.util.Map;
import java.util.TreeMap;

/** Renders analysis properties as a {@code sonar-project.properties} file readable by {@code java.util.Properties}. */
public final class SonarProjectProperties {

    private SonarProjectProperties() {}

    /** Sorted by key so the output is stable and cache-friendly; newline-terminated. */
    public static String render(Map<String, String> properties) {
        StringBuilder out = new StringBuilder();
        new TreeMap<>(properties).forEach((key, value) -> {
            escape(out, key);
            out.append('=');
            escape(out, value);
            out.append('\n');
        });
        return out.toString();
    }

    private static void escape(StringBuilder out, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\', ':', '=', '#', '!' -> out.append('\\').append(c);
                case ' ' -> out.append("\\ ");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
    }
}
