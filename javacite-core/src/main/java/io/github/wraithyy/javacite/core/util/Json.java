package io.github.wraithyy.javacite.core.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer used to merge hook settings without a JSON dependency. Objects keep key order so
 * unknown keys survive a parse/print round trip. Values: Map, List, String, BigDecimal, Boolean, null.
 */
public final class Json {

    private static final int MAX_DEPTH = 64;

    private final String s;
    private int pos;
    private int depth;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos != text.length()) {
            throw p.error("Trailing content");
        }
        return v;
    }

    public static String print(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.append('\n').toString();
    }

    private Object value() {
        if (pos >= s.length()) {
            throw error("Unexpected end");
        }
        char c = s.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw error("Nesting deeper than " + MAX_DEPTH);
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        enter();
        pos++;
        ws();
        if (peek('}')) {
            pos++;
            depth--;
            return m;
        }
        while (true) {
            ws();
            if (!peek('"')) {
                throw error("Expected string key");
            }
            String k = string();
            ws();
            expect(':');
            ws();
            m.put(k, value());
            ws();
            if (peek(',')) {
                pos++;
            } else {
                expect('}');
                depth--;
                return m;
            }
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        enter();
        pos++;
        ws();
        if (peek(']')) {
            pos++;
            depth--;
            return l;
        }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (peek(',')) {
                pos++;
            } else {
                expect(']');
                depth--;
                return l;
            }
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        pos++;
        while (pos < s.length()) {
            char c = s.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= s.length()) {
                break;
            }
            char e = s.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > s.length()) {
                        throw error("Bad unicode escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("Bad unicode escape");
                    }
                    pos += 4;
                }
                default -> throw error("Bad escape");
            }
        }
        throw error("Unterminated string");
    }

    private BigDecimal number() {
        int start = pos;
        if (peek('+')) {
            throw error("Leading '+' is not valid JSON");
        }
        while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("Unexpected character");
        }
        try {
            return new BigDecimal(s.substring(start, pos));
        } catch (NumberFormatException e) {
            pos = start;
            throw error("Bad number");
        }
    }

    private Object literal(String word, Object v) {
        if (!s.startsWith(word, pos)) {
            throw error("Unexpected token");
        }
        pos += word.length();
        return v;
    }

    private void ws() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(char c) {
        return pos < s.length() && s.charAt(pos) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("Invalid JSON at offset " + pos + ": " + msg);
    }

    private static void write(StringBuilder sb, Object v, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                indent(sb, depth + 1);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                write(sb, e.getValue(), depth + 1);
                sb.append(++i < m.size() ? ",\n" : "\n");
            }
            indent(sb, depth);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < l.size(); i++) {
                indent(sb, depth + 1);
                write(sb, l.get(i), depth + 1);
                sb.append(i + 1 < l.size() ? ",\n" : "\n");
            }
            indent(sb, depth);
            sb.append(']');
        } else if (v instanceof String str) {
            quote(sb, str);
        } else {
            sb.append(v);
        }
    }

    private static void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(depth));
    }

    private static void quote(StringBuilder sb, String str) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
