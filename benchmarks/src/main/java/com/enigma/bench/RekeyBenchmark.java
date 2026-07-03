package com.enigma.bench;

import com.enigma.EnigmaMachine;
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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(5)
public class RekeyBenchmark {

    @Param({"3"})
    public int rotorCount;

    @Param({"40"})
    public int windowSize;

    private byte[] window;
    private byte[] out;
    private EnigmaMachine reusable;
    private int seedCursor;

    @Setup
    public void setup() {
        window = new byte[windowSize];
        out = new byte[windowSize];
        for (int i = 0; i < windowSize; i++) {
            window[i] = (byte) i;
        }
        reusable = new EnigmaMachine(0, rotorCount);
        seedCursor = 0;
    }

    @Benchmark
    public void construct(Blackhole bh) {
        EnigmaMachine machine = new EnigmaMachine(seedCursor++, rotorCount);
        machine.transform(window, out);
        bh.consume(out);
        bh.consume(machine);
    }

    @Benchmark
    public void rekey(Blackhole bh) {
        reusable.rekey(seedCursor++);
        reusable.transform(window, out);
        bh.consume(out);
        bh.consume(reusable);
    }
}
