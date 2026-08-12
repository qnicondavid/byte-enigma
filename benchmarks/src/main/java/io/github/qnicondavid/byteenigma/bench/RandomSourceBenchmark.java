package io.github.qnicondavid.byteenigma.bench;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Where the speedup came from.
 *
 * <p>Building one machine draws 1275 values from a generator. {@code java.util.Random} keeps its
 * state in an {@code AtomicLong} and advances it with a compare-and-set, so a single instance can
 * be shared between threads. The key schedule never shares one, so that atomic is paid 1275 times
 * per candidate key for nothing.
 *
 * <p>This benchmark stands the JDK generator next to the plain-field reimplementation the cipher
 * uses, over the same shuffle-sized workload. The reimplementation is bit-identical, which
 * {@code Lcg48EquivalenceTest} enforces, so the difference between these two numbers is the whole
 * of the trade.
 *
 * <p>The class under measurement is package-private in the cipher, so this benchmark measures a
 * local copy of the same arithmetic. If the two ever drift the equivalence test in the core
 * module is the one that will notice, not this.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class RandomSourceBenchmark {

    /** One machine's worth of draws: five 256-element shuffles, 255 draws each. */
    private static final int DRAWS_PER_KEY = 5 * 255;

    private Random jdkRandom;
    private LocalLcg48 plainField;
    private int seed;

    @Setup
    public void setup() {
        jdkRandom = new Random(0);
        plainField = new LocalLcg48(0);
        seed = 0;
    }

    @Benchmark
    public void jdkRandom(Blackhole blackhole) {
        jdkRandom.setSeed(seed++);
        int total = 0;
        for (int i = 0; i < DRAWS_PER_KEY; i++) {
            total += jdkRandom.nextInt(256);
        }
        blackhole.consume(total);
    }

    @Benchmark
    public void plainFieldLcg(Blackhole blackhole) {
        plainField.setSeed(seed++);
        int total = 0;
        for (int i = 0; i < DRAWS_PER_KEY; i++) {
            total += plainField.nextInt(256);
        }
        blackhole.consume(total);
    }

    /** A copy of the cipher's generator, which is package-private there. */
    private static final class LocalLcg48 {

        private static final long MULTIPLIER = 0x5DEECE66DL;
        private static final long ADDEND = 0xBL;
        private static final long MASK = (1L << 48) - 1;

        private long state;

        LocalLcg48(long seed) {
            setSeed(seed);
        }

        void setSeed(long seed) {
            this.state = (seed ^ MULTIPLIER) & MASK;
        }

        private int next(int bits) {
            state = (state * MULTIPLIER + ADDEND) & MASK;
            return (int) (state >>> (48 - bits));
        }

        int nextInt(int bound) {
            int r = next(31);
            int m = bound - 1;
            if ((bound & m) == 0) {
                r = (int) ((bound * (long) r) >> 31);
            } else {
                for (int u = r; u - (r = u % bound) + m < 0; u = next(31)) {
                    // rejection loop, as the JDK does it
                }
            }
            return r;
        }
    }
}
