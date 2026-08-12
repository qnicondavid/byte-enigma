package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class InvariantPropertyTest {

    private static byte[] allValues512() {
        byte[] input = new byte[512];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        return input;
    }

    @Test
    void allFourInvariantsHoldAcrossSeedsAndRotorCounts() {
        byte[] input = allValues512();
        String unicode = "cafe € 😀 你好 test";
        for (int rotorCount = 1; rotorCount <= 6; rotorCount++) {
            for (int seed = -256; seed <= 255; seed++) {
                ByteEnigma machine = new ByteEnigma(seed, rotorCount);

                byte[] once = machine.transform(input);
                assertArrayEquals(input, machine.transform(once),
                        "reciprocity failed for seed " + seed + ", rotorCount " + rotorCount);

                for (int i = 0; i < input.length; i++) {
                    assertNotEquals(input[i], once[i],
                            "byte " + i + " mapped to itself for seed " + seed
                                    + ", rotorCount " + rotorCount);
                }

                ByteEnigma twin = new ByteEnigma(seed, rotorCount);
                assertArrayEquals(once, twin.transform(input),
                        "determinism failed for seed " + seed + ", rotorCount " + rotorCount);

                assertEquals(unicode, machine.decrypt(machine.encrypt(unicode)),
                        "utf-8 round-trip failed for seed " + seed + ", rotorCount " + rotorCount);
            }
        }
    }
}
