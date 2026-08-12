package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class InvariantSweepTest {

    @Test
    void roundTripHoldsAcrossManySeeds() {
        String message = "Sweep test: cafe € 😀 你好 0123.";
        for (int seed = -100; seed < 100; seed++) {
            ByteEnigma machine = new ByteEnigma(seed, 3);
            assertEquals(message, machine.decrypt(machine.encrypt(message)),
                    "round-trip failed for seed " + seed);
        }
    }

    @Test
    void noByteMapsToItselfAcrossManySeeds() {
        byte[] input = new byte[1024];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        for (int seed = -100; seed < 100; seed++) {
            ByteEnigma machine = new ByteEnigma(seed, 3);
            byte[] output = machine.transform(input);
            for (int i = 0; i < input.length; i++) {
                assertNotEquals(input[i], output[i],
                        "byte " + i + " mapped to itself for seed " + seed);
            }
        }
    }

    @Test
    void transformSelfInverseAcrossSeedsAndRotorCounts() {
        byte[] input = "payload\0\1\2".getBytes(StandardCharsets.UTF_8);
        for (int rotorCount = 1; rotorCount <= 6; rotorCount++) {
            for (int seed = -30; seed < 30; seed++) {
                ByteEnigma machine = new ByteEnigma(seed, rotorCount);
                assertArrayEquals(input, machine.transform(machine.transform(input)),
                        "self-inverse failed for seed " + seed + ", rotorCount " + rotorCount);
            }
        }
    }
}
