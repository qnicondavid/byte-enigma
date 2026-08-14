package io.github.qnicondavid.byteenigma.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JMH result file, read well enough to draw from.
 *
 * <p>Two figures on {@code docs/benchmarks.md} come from {@code docs/benchmarks.json}, the artifact
 * the run left behind, rather than from numbers copied into a generator by hand. A page whose whole
 * argument is that every number on it was measured cannot have its pictures typed.
 *
 * <p>This is the smallest reader that does that job. The file has a known shape and is written by a
 * tool, so nothing here needs to be a general-purpose JSON library, and neither this module nor
 * {@code core} takes a dependency for the sake of one file.
 */
final class JmhResults {

    private final List<Map<String, Object>> records;

    private JmhResults(List<Map<String, Object>> records) {
        this.records = records;
    }

    /** Reads a file written by {@code -rf json}. */
    static JmhResults load(Path file) throws IOException {
        Object root = new Reader(Files.readString(file, StandardCharsets.UTF_8)).whole();
        if (!(root instanceof List<?> array) || array.isEmpty()) {
            throw new IllegalStateException(file + " is not a JMH result array");
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object element : array) {
            if (!(element instanceof Map<?, ?> record)) {
                throw new IllegalStateException(file + " holds something that is not a record");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) record;
            records.add(typed);
        }
        return new JmhResults(records);
    }

    /**
     * The average time or throughput one benchmark reported, in whatever unit the run used.
     *
     * @param benchmark the method name, without its package or class
     * @param params    every parameter the benchmark takes, as {@code name=value}, all of them
     * @throws IllegalStateException unless exactly one record matches, so a benchmark that gains a
     *                               parameter breaks the build instead of silently picking a row
     */
    double score(String benchmark, String... params) {
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> record : records) {
            if (matches(record, benchmark, params)) {
                matches.add(record);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException(matches.size() + " records match " + benchmark
                    + " with " + String.join(", ", params) + ", which is not one");
        }
        Object metric = matches.get(0).get("primaryMetric");
        if (!(metric instanceof Map<?, ?> primary) || !(primary.get("score") instanceof Double score)) {
            throw new IllegalStateException(benchmark + " has no score");
        }
        return score;
    }

    private static boolean matches(Map<String, Object> record, String benchmark, String... params) {
        if (!(record.get("benchmark") instanceof String name)) {
            return false;
        }
        if (!name.substring(name.lastIndexOf('.') + 1).equals(benchmark)) {
            return false;
        }
        Object declared = record.get("params");
        Map<?, ?> actual = declared instanceof Map<?, ?> map ? map : Map.of();
        if (actual.size() != params.length) {
            return false;
        }
        for (String param : params) {
            int split = param.indexOf('=');
            if (split < 0) {
                throw new IllegalArgumentException("a parameter has to read name=value, not " + param);
            }
            if (!param.substring(split + 1).equals(actual.get(param.substring(0, split)))) {
                return false;
            }
        }
        return true;
    }

    /** Objects become maps, arrays become lists, numbers become doubles, and that is all of it. */
    private static final class Reader {

        private final String text;
        private int at;

        Reader(String text) {
            this.text = text;
        }

        Object whole() {
            Object value = value();
            skipSpace();
            if (at != text.length()) {
                throw new IllegalStateException("trailing text at offset " + at);
            }
            return value;
        }

        private Object value() {
            skipSpace();
            char c = text.charAt(at);
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

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            skipSpace();
            if (text.charAt(at) == '}') {
                at++;
                return map;
            }
            while (true) {
                skipSpace();
                String key = string();
                skipSpace();
                expect(':');
                map.put(key, value());
                skipSpace();
                char c = text.charAt(at++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or } at offset " + (at - 1));
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            at++;
            skipSpace();
            if (text.charAt(at) == ']') {
                at++;
                return list;
            }
            while (true) {
                list.add(value());
                skipSpace();
                char c = text.charAt(at++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or ] at offset " + (at - 1));
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = text.charAt(at++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escape = text.charAt(at++);
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                    }
                    default -> throw new IllegalStateException("bad escape at offset " + (at - 1));
                }
            }
        }

        private Double number() {
            int start = at;
            while (at < text.length() && "+-.eE0123456789".indexOf(text.charAt(at)) >= 0) {
                at++;
            }
            if (start == at) {
                throw new IllegalStateException("expected a value at offset " + at);
            }
            return Double.valueOf(text.substring(start, at));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, at)) {
                throw new IllegalStateException("expected " + word + " at offset " + at);
            }
            at += word.length();
            return value;
        }

        private void expect(char c) {
            if (text.charAt(at) != c) {
                throw new IllegalStateException("expected " + c + " at offset " + at);
            }
            at++;
        }

        private void skipSpace() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }
    }
}
