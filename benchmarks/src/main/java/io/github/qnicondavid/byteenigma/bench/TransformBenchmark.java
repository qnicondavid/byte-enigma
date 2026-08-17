package io.github.qnicondavid.byteenigma.bench;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import java.util.Arrays;
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

/** Throughput of the transform itself, once the key schedule is already built. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class TransformBenchmark {

    @Param({"64", "1024", "65536"})
    public int messageSize;

    /** Far enough past the one match in 16,777,216 that failing to find one means something. */
    private static final long SEARCH_LIMIT = 1L << 28;

    private ByteEnigma machine;
    private byte[] payload;
    private byte[] reuseBuffer;
    private long quietNonce;

    @Setup(Level.Trial)
    public void setup() {
        machine = ByteEnigma.fromPassword("benchmark-key", 3);
        payload = new byte[messageSize];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0xFF);
        }
        reuseBuffer = new byte[messageSize];
        quietNonce = nonceThatStartsWhereTheTextbookDoes(machine);
    }

    /** Allocates a fresh output array every call. */
    @Benchmark
    public byte[] allocating() {
        return machine.transform(payload);
    }

    /** Writes into a buffer the caller owns, which is what a sweep does. */
    @Benchmark
    public byte[] reusingABuffer() {
        machine.transform(payload, reuseBuffer);
        return reuseBuffer;
    }

    /** With a nonce, which costs one extra pass over the rotor offsets and nothing else. */
    @Benchmark
    public byte[] withANonce() {
        machine.transform(payload, reuseBuffer, 42L);
        return reuseBuffer;
    }

    /**
     * The nonce overload, with a nonce that leaves the rotors where the textbook path leaves them.
     *
     * <p>{@link #withANonce} measures about 5% faster than {@link #reusingABuffer} at 65,536 bytes,
     * in every run this project has recorded, and nothing in the code accounts for it. The two
     * differ by one call in front of a loop they share, and that call is one reseed and three draws
     * against 65,536 substitutions. Two things do differ. The rotors start somewhere else, which
     * changes the data the loop walks and not the code, and the compiler is looking at a different
     * overload.
     *
     * <p>This separates them. It runs the nonced overload from the textbook path's starting
     * positions, so a result beside {@code reusingABuffer} says the positions are what matter, and
     * one beside {@code withANonce} says the overload is.
     */
    @Benchmark
    public byte[] withANonceThatChangesNothing() {
        machine.transform(payload, reuseBuffer, quietNonce);
        return reuseBuffer;
    }

    /**
     * The first nonce whose starting offsets are the ones the textbook path uses, found by trying.
     *
     * <p>Nothing public reports where a rotor is, so this asks the machine instead: four bytes
     * through both overloads agree only when the two started from the same place. Three rotors put
     * that at one nonce in 16,777,216, with a false match one time in 4,294,967,296, so the
     * candidate is confirmed over a longer message before it is believed.
     */
    private static long nonceThatStartsWhereTheTextbookDoes(ByteEnigma machine) {
        byte[] probe = new byte[4];
        byte[] textbook = machine.transform(probe);
        byte[] nonced = new byte[probe.length];
        for (long nonce = 0L; nonce < SEARCH_LIMIT; nonce++) {
            machine.transform(probe, nonced, nonce);
            if (Arrays.equals(textbook, nonced)) {
                byte[] longer = new byte[4096];
                if (Arrays.equals(machine.transform(longer), machine.transform(longer, nonce))) {
                    return nonce;
                }
            }
        }
        throw new IllegalStateException(
                "no nonce below " + SEARCH_LIMIT + " starts the rotors where the textbook path does");
    }
}
