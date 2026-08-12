package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The contract a caller can rely on: reciprocity, determinism, no fixed points, honest errors. */
class ByteEnigmaTest {

    private static ByteEnigma machine(String passphrase) {
        return ByteEnigma.fromPassword(passphrase, 3);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void transformIsItsOwnInverse() {
        ByteEnigma machine = machine("reciprocity");
        byte[] input = bytes("Reciprocity check 123.");
        assertArrayEquals(input, machine.transform(machine.transform(input)));
    }

    @Test
    void roundTripsArbitraryBytesIncludingUtf8() {
        ByteEnigma machine = machine("unicode");
        byte[] input = bytes("cafe € 😀 你好");
        assertArrayEquals(input, machine.transform(machine.transform(input)));
    }

    @Test
    void roundTripsBinaryPayloads() {
        ByteEnigma machine = machine("bytes");
        byte[] input = bytes("binary\0\1\2ÿpayload");
        assertArrayEquals(input, machine.transform(machine.transform(input)));
    }

    @Test
    void roundTripsTheEmptyMessage() {
        ByteEnigma machine = machine("empty");
        assertEquals(0, machine.transform(new byte[0]).length);
    }

    @Test
    void theSameKeyAlwaysProducesTheSameCiphertext() {
        byte[] input = bytes("Determinism.");
        assertArrayEquals(machine("k").transform(input), machine("k").transform(input));
    }

    @Test
    void differentKeysProduceDifferentCiphertext() {
        byte[] input = bytes("Determinism.");
        assertNotEquals(
                Arrays.toString(machine("k1").transform(input)),
                Arrays.toString(machine("k2").transform(input)));
    }

    @Test
    void noByteEverEncryptsToItself() {
        ByteEnigma machine = machine("no-fixed-point");
        byte[] input = new byte[4096];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        byte[] output = machine.transform(input);
        for (int i = 0; i < input.length; i++) {
            assertNotEquals(input[i], output[i], "byte at index " + i + " mapped to itself");
        }
    }

    @Test
    void writingIntoABufferMatchesAllocatingOne() {
        ByteEnigma machine = machine("buffer");
        byte[] input = bytes("reuse this buffer");
        byte[] expected = machine.transform(input);
        byte[] output = new byte[input.length + 32];
        assertEquals(input.length, machine.transform(input, output));
        for (int i = 0; i < input.length; i++) {
            assertEquals(expected[i], output[i]);
        }
    }

    @Test
    void writingIntoABufferLeavesTheTailAlone() {
        ByteEnigma machine = machine("buffer");
        byte[] input = bytes("reuse this buffer");
        byte[] output = new byte[input.length + 8];
        Arrays.fill(output, (byte) 0x7F);
        assertEquals(input.length, machine.transform(input, output));
        for (int i = input.length; i < output.length; i++) {
            assertEquals((byte) 0x7F, output[i], "tail byte " + i + " was written");
        }
    }

    @Test
    void rejectsAnUndersizedOutputBuffer() {
        ByteEnigma machine = machine("buffer");
        assertThrows(() -> machine.transform(new byte[10], new byte[5]));
    }

    @Test
    void rejectsRotorCountsOutsideTheAllowedRange() {
        assertThrows(() -> new ByteEnigma(123, 0));
        assertThrows(() -> new ByteEnigma(123, -1));
        assertThrows(() -> new ByteEnigma(1, ByteEnigma.MAX_ROTOR_COUNT + 1));
    }

    @Test
    void acceptsRotorCountsUpToTheCap() {
        assertEquals(ByteEnigma.MAX_ROTOR_COUNT,
                new ByteEnigma(1, ByteEnigma.MAX_ROTOR_COUNT).rotorCount());
    }

    @Test
    void reportsTheKeyItIsHolding() {
        ByteEnigma machine = new ByteEnigma(4242, 3);
        assertEquals(4242, machine.key());
        machine.rekey(-7);
        assertEquals(-7, machine.key());
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isBlank(),
                    "exception should say what was wrong");
        }
    }
}
