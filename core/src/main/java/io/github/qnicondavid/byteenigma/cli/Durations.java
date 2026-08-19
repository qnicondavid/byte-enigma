package io.github.qnicondavid.byteenigma.cli;

import java.util.Locale;

/**
 * Turns a number of seconds into something a person can read at a glance.
 *
 * <p>Formatted under {@code Locale.ROOT} rather than under the machine's own, because these
 * strings go into an hour of progress lines and into the log pasted into
 * {@code docs/keyspace-sweep.md}. On a comma-decimal machine an unqualified format prints 3,79 h
 * where that page prints 3.79 h, and a reader could not line their own run up against it.
 */
final class Durations {

    private Durations() {
    }

    static String format(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return "unknown";
        }
        if (seconds < 90.0) {
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        double minutes = seconds / 60.0;
        if (minutes < 90.0) {
            return String.format(Locale.ROOT, "%.1f min", minutes);
        }
        double hours = minutes / 60.0;
        if (hours < 48.0) {
            return String.format(Locale.ROOT, "%.2f h", hours);
        }
        return String.format(Locale.ROOT, "%.2f days", hours / 24.0);
    }
}
