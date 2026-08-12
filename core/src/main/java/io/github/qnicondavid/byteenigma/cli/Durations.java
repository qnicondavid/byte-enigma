package io.github.qnicondavid.byteenigma.cli;

/** Turns a number of seconds into something a person can read at a glance. */
final class Durations {

    private Durations() {
    }

    static String format(double seconds) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            return "unknown";
        }
        if (seconds < 90.0) {
            return String.format("%.1f s", seconds);
        }
        double minutes = seconds / 60.0;
        if (minutes < 90.0) {
            return String.format("%.1f min", minutes);
        }
        double hours = minutes / 60.0;
        if (hours < 48.0) {
            return String.format("%.2f h", hours);
        }
        return String.format("%.2f days", hours / 24.0);
    }
}
