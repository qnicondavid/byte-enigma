package io.github.qnicondavid.byteenigma.bench;

import io.github.qnicondavid.byteenigma.breaker.CribMatcher;
import io.github.qnicondavid.byteenigma.breaker.QuadgramScorer;
import io.github.qnicondavid.byteenigma.breaker.QuadgramSearch;
import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import java.nio.charset.StandardCharsets;
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
 * The cost of one candidate key, measured the way the sweep actually spends it.
 *
 * <p>This benchmark used to construct a fresh machine per candidate, long after the sweep had
 * stopped doing that. A benchmark that models something the code no longer does is worse than no
 * benchmark, because it looks like evidence. These four cases are what a sweep does now, split
 * so the share of each is visible: rekeying alone, rekeying plus a crib window, rekeying plus a
 * full decrypt, and rekeying plus a full decrypt plus a language score.
 *
 * <p>Reading them in order shows why the crib attack is only fractionally faster than the
 * ciphertext-only one: almost all of the time goes into rebuilding the key schedule, and neither
 * attack can avoid that.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class CandidateBenchmark {

    private static final String TEXT =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR WITHOUT WARNING "
            + "SO WE MUST DEFEND THE COAST AT ONCE AND SEND WORD BACK ALONG THE NORTHERN ROAD";
    private static final String CRIB = "SOUTHERN HARBOUR";

    @Param({"3"})
    public int rotorCount;

    private ByteEnigma machine;
    private QuadgramScorer scorer;
    private CribMatcher cribMatcher;
    private QuadgramSearch quadgramSearch;
    private byte[] ciphertext;
    private byte[] scratch;
    private int cribOffset;
    private int cribEnd;
    private int key;

    @Setup
    public void setup() {
        machine = new ByteEnigma(0, rotorCount);
        scorer = QuadgramScorer.fromResource();
        ciphertext = new ByteEnigma(12345, rotorCount)
                .transform(TEXT.getBytes(StandardCharsets.UTF_8));
        scratch = new byte[ciphertext.length];
        cribOffset = TEXT.indexOf(CRIB);
        cribEnd = cribOffset + CRIB.length();
        cribMatcher = new CribMatcher(CRIB.getBytes(StandardCharsets.UTF_8), cribOffset);
        quadgramSearch = new QuadgramSearch(scorer);
        key = 0;
    }

    /** The floor: what every candidate costs before anything is decrypted. */
    @Benchmark
    public void rekeyOnly(Blackhole blackhole) {
        machine.rekey(key++);
        blackhole.consume(machine);
    }

    /** What the crib attack costs: rekey, then decrypt only the bytes the crib covers. */
    @Benchmark
    public void rekeyAndCribWindow(Blackhole blackhole) {
        machine.rekey(key++);
        machine.transformWindow(ciphertext, scratch, cribOffset, cribEnd);
        blackhole.consume(scratch);
    }

    /** Rekey plus a full decrypt, without scoring it. */
    @Benchmark
    public void rekeyAndFullTransform(Blackhole blackhole) {
        machine.rekey(key++);
        machine.transform(ciphertext, scratch);
        blackhole.consume(scratch);
    }

    /** What the ciphertext-only attack costs, end to end. */
    @Benchmark
    public void rekeyTransformAndScore(Blackhole blackhole) {
        machine.rekey(key++);
        int length = machine.transform(ciphertext, scratch);
        blackhole.consume(scorer.score(scratch, length));
    }

    /**
     * The crib evaluator exactly as the sweep calls it, including the Candidate it builds on a hit.
     *
     * <p>The four cases above measure the pieces. These two measure what the sweep actually invokes,
     * which is the number any comparison between the attacks should be drawn from: the pieces leave
     * out the candidate allocation, and the ciphertext-only path pays it on every key rather than
     * only on survivors.
     */
    @Benchmark
    public void cribEvaluator(Blackhole blackhole) {
        blackhole.consume(cribMatcher.evaluate(key++, machine, ciphertext, scratch));
    }

    /** The ciphertext-only evaluator exactly as the sweep calls it. */
    @Benchmark
    public void languageEvaluator(Blackhole blackhole) {
        blackhole.consume(quadgramSearch.evaluate(key++, machine, ciphertext, scratch));
    }

    /** The no-fixed-point filter, which rules out crib positions without trying any key at all. */
    @Benchmark
    public void admissibilityFilter(Blackhole blackhole) {
        blackhole.consume(CribMatcher.offsetAdmissible(
                ciphertext, CRIB.getBytes(StandardCharsets.UTF_8), cribOffset));
    }
}
