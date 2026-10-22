package com.miwi.gitworktree.ui;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JSON object codec for the flat string-to-string localization files. */
final class FlatJson {
    private FlatJson() {}

    static Map<String, String> parse(String source) {
        Parser parser = new Parser(source);
        return parser.parseObject();
    }

    static String write(Map<String, String> values) {
        StringBuilder result = new StringBuilder("{\n");
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (index++ > 0) result.append(",\n");
            result.append("  ").append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
        }
        return result.append("\n}\n").toString();
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) result.append(String.format("\\u%04x", (int) ch));
                    else result.append(ch);
                }
            }
        }
        return result.append('\"').toString();
    }

    private static final class Parser {
        private final String source;
        private int cursor;

        private Parser(String source) { this.source = source; }

        private Map<String, String> parseObject() {
            skipWhitespace();
            expect('{');
            Map<String, String> result = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return finish(result);
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                String value = readString();
                if (result.putIfAbsent(key, value) != null) throw error("Duplicate JSON key: " + key);
                skipWhitespace();
                if (consume('}')) return finish(result);
                expect(',');
            }
        }

        private Map<String, String> finish(Map<String, String> result) {
            skipWhitespace();
            if (cursor != source.length()) throw error("Unexpected content after JSON object.");
            return result;
        }

        private String readString() {
            expect('\"');
            StringBuilder result = new StringBuilder();
            while (cursor < source.length()) {
                char ch = source.charAt(cursor++);
                if (ch == '\"') return result.toString();
                if (ch < 0x20) throw error("Unescaped control character in JSON string.");
                if (ch != '\\') {
                    result.append(ch);
                    continue;
                }
                if (cursor >= source.length()) throw error("Incomplete JSON escape.");
                char escaped = source.charAt(cursor++);
                switch (escaped) {
                    case '\"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(readUnicode());
                    default -> throw error("Invalid JSON escape: \\" + escaped);
                }
            }
            throw error("Unterminated JSON string.");
        }

        private char readUnicode() {
            if (cursor + 4 > source.length()) throw error("Incomplete Unicode escape.");
            try {
                char value = (char) Integer.parseInt(source.substring(cursor, cursor + 4), 16);
                cursor += 4;
                return value;
            } catch (NumberFormatException exception) {
                throw error("Invalid Unicode escape.");
            }
        }

        private void expect(char expected) {
            if (!consume(expected)) throw error("Expected '" + expected + "'.");
        }

        private boolean consume(char expected) {
            if (cursor < source.length() && source.charAt(cursor) == expected) {
                cursor++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " (offset " + cursor + ")");
        }
    }
}
