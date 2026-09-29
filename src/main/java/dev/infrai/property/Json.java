package dev.infrai.property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        if (value == null) out.append("null");
        else if (value instanceof String text) quote(out, text);
        else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean comma = false;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (comma) out.append(',');
                quote(out, entry.getKey().toString());
                out.append(':');
                append(out, entry.getValue());
                comma = true;
            }
            out.append('}');
        } else if (value instanceof Iterable<?> values) {
            out.append('[');
            boolean comma = false;
            for (Object item : values) {
                if (comma) out.append(',');
                append(out, item);
                comma = true;
            }
            out.append(']');
        } else throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    static Map<String, Object> parseObject(String source) {
        Object value = new Parser(source).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected a JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static final class Parser {
        private final String source;
        private int index;

        Parser(String source) { this.source = source; }

        Object parse() {
            Object value = value();
            whitespace();
            if (index != source.length()) fail("Unexpected trailing content");
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= source.length()) return fail("Expected a value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            whitespace();
            if (take('}')) return map;
            do {
                whitespace();
                String key = string();
                whitespace();
                if (!take(':')) fail("Expected ':'");
                map.put(key, value());
                whitespace();
                if (take('}')) return map;
            } while (take(','));
            return fail("Expected ',' or '}'");
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            index++;
            whitespace();
            if (take(']')) return list;
            do {
                list.add(value());
                whitespace();
                if (take(']')) return list;
            } while (take(','));
            return fail("Expected ',' or ']'");
        }

        private String string() {
            if (!take('"')) return fail("Expected a string");
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') out.append(c);
                else {
                    if (index >= source.length()) fail("Incomplete escape");
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> out.append(escaped);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (index + 4 > source.length()) fail("Incomplete unicode escape");
                            out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                            index += 4;
                        }
                        default -> fail("Unknown escape");
                    }
                }
            }
            return fail("Unclosed string");
        }

        private Object number() {
            int start = index;
            while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            if (start == index) return fail("Expected a value");
            String token = source.substring(start, index);
            return token.contains(".") || token.contains("e") || token.contains("E")
                ? Double.parseDouble(token) : Long.parseLong(token);
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) return fail("Invalid literal");
            index += token.length();
            return value;
        }

        private boolean take(char expected) {
            if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private void whitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private <T> T fail(String message) {
            throw new IllegalArgumentException(message + " at character " + index);
        }
    }
}
