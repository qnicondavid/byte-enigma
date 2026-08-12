package io.github.qnicondavid.byteenigma.bench;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
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
 * Building a key schedule from scratch against rebuilding one in place.
 *
 * <p>The two are byte-identical, which {@code RekeyEquivalenceTest} enforces, so the gap between
 * these numbers is pure allocation and garbage collection that a sweep gets to skip.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class KeyScheduleBenchmark {

    @Param({"1", "3", "8"})
    public int rotorCount;

    private ByteEnigma reusable;
    private int key;

    @Setup
    public void setup() {
        reusable = new ByteEnigma(0, rotorCount);
        key = 0;
    }

    @Benchmark
    public void construct(Blackhole blackhole) {
        blackhole.consume(new ByteEnigma(key++, rotorCount));
    }

    @Benchmark
    public void rekeyInPlace(Blackhole blackhole) {
        reusable.rekey(key++);
        blackhole.consume(reusable);
    }
}
