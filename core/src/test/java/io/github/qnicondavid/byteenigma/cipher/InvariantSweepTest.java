package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The four invariants, held across many keys and rotor counts rather than at one lucky key.
 *
 * <p>Reciprocity and the absence of fixed points are structural: they follow from the reflector
 * being a fixed-point-free involution, and they hold or the design is wrong. Sweeping them is
 * how a refactor gets caught before the golden vector notices.
 */
class InvariantSweepTest {

    private static byte[] everyByteValueTwice() {
        byte[] input = new byte[512];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        return input;
    }

    @Test
    void reciprocityDeterminismAndNoFixedPointsHoldAcrossKeysAndRotorCounts() {
        byte[] input = everyByteValueTwice();
        byte[] unicode = "cafe € 😀 你好 test".getBytes(StandardCharsets.UTF_8);
        for (int rotorCount = 1; rotorCount <= 6; rotorCount++) {
            for (int key = -256; key <= 255; key++) {
                ByteEnigma machine = new ByteEnigma(key, rotorCount);

                byte[] once = machine.transform(input);
                assertArrayEquals(input, machine.transform(once),
                        "reciprocity failed at key " + key + ", rotors " + rotorCount);

                for (int i = 0; i < input.length; i++) {
                    assertNotEquals(input[i], once[i],
                            "byte " + i + " mapped to itself at key " + key + ", rotors " + rotorCount);
                }

                assertArrayEquals(once, new ByteEnigma(key, rotorCount).transform(input),
                        "determinism failed at key " + key + ", rotors " + rotorCount);

                assertArrayEquals(unicode, machine.transform(machine.transform(unicode)),
                        "utf-8 round trip failed at key " + key + ", rotors " + rotorCount);
            }
        }
    }

    @Test
    void noByteMapsToItselfAcrossAWideKeyRange() {
        byte[] input = new byte[1024];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        for (int key = -100; key < 100; key++) {
            byte[] output = new ByteEnigma(key, 3).transform(input);
            for (int i = 0; i < input.length; i++) {
                assertNotEquals(input[i], output[i], "byte " + i + " mapped to itself at key " + key);
            }
        }
    }
}
