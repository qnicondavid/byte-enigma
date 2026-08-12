package io.github.qnicondavid.byteenigma.bench;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class TransformBenchmark {

    @Param({"64", "1024", "65536"})
    public int messageSize;

    private ByteEnigma machine;
    private byte[] payload;
    private byte[] reuseBuffer;

    @Setup(Level.Trial)
    public void setup() {
        machine = ByteEnigma.fromPassword("benchmark-key", 3);
        payload = new byte[messageSize];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xFF);
        }
        reuseBuffer = new byte[messageSize];
    }

    @Benchmark
    public byte[] transform() {
        return machine.transform(payload);
    }

    @Benchmark
    public byte[] transformReusingBuffer() {
        machine.transform(payload, reuseBuffer);
        return reuseBuffer;
    }
}
