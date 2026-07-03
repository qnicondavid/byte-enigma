package com.enigma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class RekeyEquivalenceTest {

    private static byte[] comprehensiveInput() {
        byte[] input = new byte[600];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        return input;
    }

    private static int[] seeds() {
        int[] small = new int[601];
        for (int s = -300; s <= 300; s++) {
            small[s + 300] = s;
        }
        int[] extra = {
            0, 1, -1, 42, -42, 65536, -65536,
            123456789, -123456789,
            Integer.MAX_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE - 1, Integer.MIN_VALUE + 1
        };
        int[] all = new int[small.length + extra.length];
        System.arraycopy(small, 0, all, 0, small.length);
        System.arraycopy(extra, 0, all, small.length, extra.length);
        return all;
    }

    @Test
    void rekeyMatchesFreshConstructionAcrossSeedsAndRotorCounts() {
        byte[] input = comprehensiveInput();
        for (int rotorCount = 1; rotorCount <= 6; rotorCount++) {
            EnigmaMachine reused = new EnigmaMachine(0, rotorCount);
            for (int seed : seeds()) {
                reused.rekey(seed);
                byte[] fresh = new EnigmaMachine(seed, rotorCount).transform(input);
                byte[] viaRekey = reused.transform(input);
                assertArrayEquals(fresh, viaRekey,
                        "mismatch at seed=" + seed + " rotorCount=" + rotorCount);
            }
        }
    }

    @Test
    void reusedMachineStaysCorrectAcrossManySequentialRekeys() {
        byte[] input = comprehensiveInput();
        int rotorCount = 3;
        EnigmaMachine reused = new EnigmaMachine(0, rotorCount);
        int[] sequence = {7, -7, 100, Integer.MIN_VALUE, 3, 3, -100000, Integer.MAX_VALUE, 0, 42};
        for (int seed : sequence) {
            reused.rekey(seed);
            byte[] fresh = new EnigmaMachine(seed, rotorCount).transform(input);
            byte[] viaRekey = reused.transform(input);
            assertArrayEquals(fresh, viaRekey,
                    "reused machine diverged after rekey to seed=" + seed);
        }
    }

    @Test
    void repeatedRekeyToSameSeedIsStable() {
        byte[] input = comprehensiveInput();
        int rotorCount = 4;
        int seed = -12345;
        EnigmaMachine fresh = new EnigmaMachine(seed, rotorCount);
        byte[] expected = fresh.transform(input);
        EnigmaMachine reused = new EnigmaMachine(999, rotorCount);
        for (int repeat = 0; repeat < 5; repeat++) {
            reused.rekey(seed);
            assertArrayEquals(expected, reused.transform(input),
                    "repeated rekey to same seed diverged on repeat " + repeat);
        }
    }
}
