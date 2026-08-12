package io.github.qnicondavid.byteenigma.cipher;

/**
 * A drop-in replacement for {@link java.util.Random} that produces bit-identical output
 * without the atomic compare-and-set on every draw.
 *
 * <p>{@code java.util.Random} stores its state in an {@code AtomicLong} and advances it with a
 * CAS loop so that a single instance can be shared across threads. The key schedule in
 * {@link ByteEnigma} never shares a generator, so that CAS is pure overhead - and it is paid
 * 1275 times per key, which is the dominant cost of a brute-force sweep.
 *
 * <p>The algorithm is the 48-bit linear congruential generator specified in the
 * {@code java.util.Random} class documentation. That specification is part of the Java platform
 * contract and cannot change between releases, so reimplementing it here is safe: the same seed
 * yields the same sequence on any conforming JVM. {@code Lcg48EquivalenceTest} pins this by
 * comparing several million draws against the real thing.
 *
 * <p>Not thread-safe, by design. Give each thread its own instance.
 */
final class Lcg48 {

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long ADDEND = 0xBL;
    private static final long MASK = (1L << 48) - 1;

    private long state;

    Lcg48(long seed) {
        this.state = (seed ^ MULTIPLIER) & MASK;
    }

    void setSeed(long seed) {
        this.state = (seed ^ MULTIPLIER) & MASK;
    }

    private int next(int bits) {
        state = (state * MULTIPLIER + ADDEND) & MASK;
        return (int) (state >>> (48 - bits));
    }

    /**
     * Returns a value in {@code [0, bound)}, matching {@code java.util.Random.nextInt(int)}
     * draw for draw, including its rejection loop for non-power-of-two bounds.
     */
    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            r = (int) ((bound * (long) r) >> 31);
        } else {
            for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                // Reject draws that would bias the low end of the range, exactly as the JDK does.
            }
        }
        return r;
    }
}
