package io.github.qnicondavid.byteenigma.search;

import java.util.List;

/**
 * What a sweep found, how much of the keyspace it covered, and how long that took.
 *
 * <p>{@link #keysPerSecond()} is measured, not modelled: it is the range actually finished
 * divided by wall-clock time. Every throughput figure this project publishes comes from here.
 *
 * @param best         the top candidates, highest score first, at most as many as the sweep was asked to keep
 * @param keysTried    how many keys were evaluated
 * @param elapsedNanos wall-clock duration, which for a parallel sweep is the span of the whole run
 */
public record SweepResult(List<Candidate> best, long keysTried, long elapsedNanos) {

    public SweepResult {
        best = List.copyOf(best);
    }

    /** The highest-scoring candidate, or {@code null} if nothing was kept. */
    public Candidate top() {
        return best.isEmpty() ? null : best.get(0);
    }

    public double elapsedSeconds() {
        return elapsedNanos / 1_000_000_000.0;
    }

    /** Keys evaluated per second of wall clock. Zero if the sweep was too short to time. */
    public double keysPerSecond() {
        double seconds = elapsedSeconds();
        return seconds > 0.0 ? keysTried / seconds : 0.0;
    }

    /** How long this run's measured rate says the full 32-bit keyspace would take. */
    public double fullKeyspaceSeconds() {
        double rate = keysPerSecond();
        return rate > 0.0 ? (1L << 32) / rate : Double.POSITIVE_INFINITY;
    }
}
