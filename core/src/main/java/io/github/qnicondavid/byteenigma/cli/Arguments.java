package io.github.qnicondavid.byteenigma.cli;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A small parser for {@code --name value} and {@code --flag} arguments.
 *
 * <p>Deliberately not a library. The command line here has a dozen options and pulling in a
 * dependency to read them would be a worse trade than forty lines of parsing, especially in a
 * project whose whole pitch is that you can read all of it.
 */
final class Arguments {

    private final Map<String, String> values = new LinkedHashMap<>();

    private Arguments() {
    }

    /** Parses {@code args} from {@code offset} onwards. A bare {@code --flag} maps to {@code "true"}. */
    static Arguments parse(String[] args, int offset) {
        Arguments parsed = new Arguments();
        for (int i = offset; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--")) {
                throw new UsageException("unexpected argument: " + token);
            }
            String name = token.substring(2);
            if (name.isEmpty()) {
                throw new UsageException("empty option name");
            }
            int equals = name.indexOf('=');
            if (equals >= 0) {
                parsed.values.put(name.substring(0, equals), name.substring(equals + 1));
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                parsed.values.put(name, args[++i]);
            } else {
                parsed.values.put(name, "true");
            }
        }
        return parsed;
    }

    boolean has(String name) {
        return values.containsKey(name);
    }

    boolean flag(String name) {
        return has(name) && Boolean.parseBoolean(values.get(name));
    }

    String require(String name) {
        String value = values.get(name);
        if (value == null) {
            throw new UsageException("missing required option --" + name);
        }
        return value;
    }

    String optional(String name, String fallback) {
        return values.getOrDefault(name, fallback);
    }

    int intValue(String name, int fallback) {
        String value = values.get(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.replace("_", ""));
        } catch (NumberFormatException malformed) {
            throw new UsageException("--" + name + " expects a whole number but got " + value);
        }
    }

    long longValue(String name, long fallback) {
        String value = values.get(name);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.replace("_", ""));
        } catch (NumberFormatException malformed) {
            throw new UsageException("--" + name + " expects a whole number but got " + value);
        }
    }

    /** Rejects anything not in the allowed set, so a typo is an error rather than a silent default. */
    void rejectUnknown(String... allowed) {
        for (String name : values.keySet()) {
            boolean known = false;
            for (String candidate : allowed) {
                if (candidate.equals(name)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new UsageException("unknown option --" + name);
            }
        }
    }

    /** Thrown for anything the user can fix by reading the usage text. */
    static final class UsageException extends RuntimeException {

        UsageException(String message) {
            super(message);
        }
    }
}
