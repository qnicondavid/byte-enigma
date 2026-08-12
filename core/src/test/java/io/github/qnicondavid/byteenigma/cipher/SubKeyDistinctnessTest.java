package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubKeyDistinctnessTest {

    private static final int MAX_ROTOR_COUNT = 8;

    private static int[] seeds() {
        int span = 2001;
        int[] extra = {
            0, -1, -2, -3,
            Integer.MIN_VALUE, Integer.MAX_VALUE,
            Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1
        };
        int[] all = new int[span + extra.length];
        int idx = 0;
        for (int s = -1000; s <= 1000; s++) {
            all[idx++] = s;
        }
        System.arraycopy(extra, 0, all, idx, extra.length);
        return all;
    }

    @Test
    void allComponentSubSeedsPairwiseDistinct() {
        for (int seed : seeds()) {
            for (int rotorCount = 1; rotorCount <= MAX_ROTOR_COUNT; rotorCount++) {
                Set<Integer> seen = new HashSet<>();
                assertTrue(seen.add(ByteEnigma.mix(seed, 0)),
                        "position RNG sub-seed collided at seed=" + seed
                                + " rotorCount=" + rotorCount);
                for (int i = 1; i <= rotorCount; i++) {
                    assertTrue(seen.add(ByteEnigma.mix(seed, i)),
                            "rotor sub-seed collided (tag=" + i + ") at seed=" + seed
                                    + " rotorCount=" + rotorCount);
                }
                assertTrue(seen.add(ByteEnigma.mix(seed, -1)),
                        "reflector sub-seed collided at seed=" + seed
                                + " rotorCount=" + rotorCount);
                assertTrue(seen.add(ByteEnigma.mix(seed, -2)),
                        "plugboard sub-seed collided at seed=" + seed
                                + " rotorCount=" + rotorCount);
                assertEquals(rotorCount + 3, seen.size(),
                        "expected all sub-seeds distinct at seed=" + seed
                                + " rotorCount=" + rotorCount);
            }
        }
    }

    @Test
    void rotorZeroNeverCollidesWithReflector() {
        for (int seed : seeds()) {
            assertNotEquals(ByteEnigma.mix(seed, 1), ByteEnigma.mix(seed, -1),
                    "rotor0/reflector sub-seeds collided at seed=" + seed);
        }
    }

    @Test
    void rotorOneNeverCollidesWithPlugboardAtSeedMinusOne() {
        assertNotEquals(ByteEnigma.mix(-1, 2), ByteEnigma.mix(-1, -2),
                "rotor1/plugboard sub-seeds collided at seed=-1");
    }
}
