package io.github.qnicondavid.byteenigma.bench;

import io.github.qnicondavid.byteenigma.breaker.QuadgramScorer;
import io.github.qnicondavid.byteenigma.breaker.QuadgramSearch;
import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.SeedSweep;
import io.github.qnicondavid.byteenigma.search.SweepResult;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * What a sweep costs that its evaluator does not.
 *
 * <p>{@link CandidateBenchmark} measures the evaluator on one unloaded thread.
 * {@code docs/keyspace-sweep.md} reports keys/sec for whole runs of {@link SeedSweep}. The two did
 * not reconcile: a thread inside the sweep did between 1.7 and 3.9 times less work than a thread
 * inside JMH, on the same machine and the same message, and the gap widened with the thread count.
 * Hybrid cores and sustained-load clocks would account for some of it, and so would the work
 * {@code SeedSweep} does per candidate that the evaluator does not: the loop, the counters, the
 * leaderboard comparison. Nothing separated the two, because there was a benchmark of the evaluator
 * and none of the sweep.
 *
 * <p>This is that one. It drives the same evaluator over the same message through the real sweep, so
 * the distance from {@code CandidateBenchmark} at {@code messageSize = 234} is the sweep's own
 * overhead, measured instead of argued about. Divide {@value #KEYS} by the millisecond figure to get
 * keys/sec and the comparison is direct.
 *
 * <p>Each operation builds its own {@code SeedSweep} and its own worker threads. A four billion key
 * run amortises that far better than this does, so it is counted here and is not counted there;
 * against an operation of roughly a third of a second it is noise, but it is the honest direction
 * for the bias to run.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class SweepBenchmark {

    private static final int ROTOR_COUNT = 3;

    /** Enough keys that thread setup is noise, few enough that one operation stays short. */
    static final int KEYS = 1 << 16;

    /** The size the full keyspace sweep ran against, so its keys/sec and these describe one job. */
    private static final int MESSAGE_SIZE = 234;

    /** What the command line keeps by default, and what the published sweep kept. */
    private static final int LEADERBOARD = 10;

    @Param({"1", "2"})
    public int threads;

    private byte[] ciphertext;
    private QuadgramSearch quadgramSearch;

    @Setup
    public void setup() {
        ciphertext = new ByteEnigma(12345, ROTOR_COUNT).transform(Messages.plaintext(MESSAGE_SIZE));
        quadgramSearch = new QuadgramSearch(QuadgramScorer.fromResource());
    }

    /** A real ciphertext-only sweep of {@value #KEYS} keys, leaderboard and counters included. */
    @Benchmark
    public void sweepScoringEnglish(Blackhole blackhole) {
        SeedSweep<ByteEnigma> sweep =
                new SeedSweep<>(() -> new ByteEnigma(0, ROTOR_COUNT), LEADERBOARD);
        SweepResult result = sweep.sweepParallel(0L, KEYS, ciphertext, quadgramSearch, threads);
        blackhole.consume(result);
    }
}
