package io.github.qnicondavid.byteenigma.bench;

import java.nio.charset.StandardCharsets;

/**
 * The plaintext the benchmarks share.
 *
 * <p>{@link CandidateBenchmark} and {@link SweepBenchmark} are only worth comparing with each other,
 * or with the rates in {@code docs/keyspace-sweep.md}, if they are working on the same text at the
 * same size. This builds it: English of any requested length with the crib always at
 * {@link #CRIB_OFFSET}, so the crib window covers the same span whatever the message length.
 *
 * <p>At 160 bytes the result is byte for byte the message the first published benchmark run used,
 * which makes that size a control. It has to come back with the same numbers.
 */
final class Messages {

    /** Exactly 49 bytes, which is the whole reason the crib always starts at 49. */
    private static final String PREFIX = "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE ";

    private static final String TAIL =
            " WITHOUT WARNING SO WE MUST DEFEND THE COAST AT ONCE AND SEND WORD BACK ALONG THE "
            + "NORTHERN ROAD AND HOLD THE LINE UNTIL THE RELIEF COLUMN ARRIVES AT FIRST LIGHT";

    /** The fragment the known-plaintext attack is handed. */
    static final String CRIB = "SOUTHERN HARBOUR";

    /** Where {@link #CRIB} sits in every message built here. */
    static final int CRIB_OFFSET = PREFIX.length();

    /** The shortest message with room for the crib where it belongs. */
    static final int SMALLEST = PREFIX.length() + CRIB.length();

    private Messages() {
    }

    /**
     * English of exactly {@code size} bytes, with {@link #CRIB} at {@link #CRIB_OFFSET}.
     *
     * @throws IllegalArgumentException if the size cannot hold the crib at that offset
     */
    static byte[] plaintext(int size) {
        if (size < SMALLEST) {
            throw new IllegalArgumentException("a message of " + size + " bytes has no room for the "
                    + "crib at " + CRIB_OFFSET + "; the smallest that has is " + SMALLEST);
        }
        StringBuilder text = new StringBuilder(size + TAIL.length());
        text.append(PREFIX).append(CRIB);
        while (text.length() < size) {
            text.append(TAIL);
        }
        text.setLength(size);
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }
}
