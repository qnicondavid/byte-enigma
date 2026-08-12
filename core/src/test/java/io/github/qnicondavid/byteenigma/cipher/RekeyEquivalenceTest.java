package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * A sweep rekeys one machine four billion times instead of building four billion machines. That
 * is only legitimate if the two are indistinguishable, which is what this checks - at the edges
 * of the keyspace, across rotor counts, and after long chains of rekeys where any leftover state
 * would have had time to show itself.
 */
class RekeyEquivalenceTest {

    private static byte[] input() {
        byte[] input = new byte[600];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        return input;
    }

    private static int[] keys() {
        int[] keys = new int[601 + 13];
        int index = 0;
        for (int key = -300; key <= 300; key++) {
            keys[index++] = key;
        }
        int[] extra = {
            0, 1, -1, 42, -42, 65536, -65536, 123456789, -123456789,
            Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE - 1, Integer.MIN_VALUE + 1
        };
        System.arraycopy(extra, 0, keys, index, extra.length);
        return keys;
    }

    @Test
    void rekeyingMatchesBuildingAfreshAcrossKeysAndRotorCounts() {
        byte[] input = input();
        for (int rotorCount = 1; rotorCount <= 6; rotorCount++) {
            ByteEnigma reused = new ByteEnigma(0, rotorCount);
            for (int key : keys()) {
                reused.rekey(key);
                assertArrayEquals(new ByteEnigma(key, rotorCount).transform(input), reused.transform(input),
                        "diverged at key " + key + ", rotors " + rotorCount);
            }
        }
    }

    @Test
    void aReusedMachineStaysCorrectDownALongChainOfRekeys() {
        byte[] input = input();
        ByteEnigma reused = new ByteEnigma(0, 3);
        int[] sequence = {7, -7, 100, Integer.MIN_VALUE, 3, 3, -100000, Integer.MAX_VALUE, 0, 42};
        for (int key : sequence) {
            reused.rekey(key);
            assertArrayEquals(new ByteEnigma(key, 3).transform(input), reused.transform(input),
                    "diverged after rekeying to " + key);
        }
    }

    @Test
    void rekeyingToTheSameKeyRepeatedlyChangesNothing() {
        byte[] input = input();
        byte[] expected = new ByteEnigma(-12345, 4).transform(input);
        ByteEnigma reused = new ByteEnigma(999, 4);
        for (int repeat = 0; repeat < 5; repeat++) {
            reused.rekey(-12345);
            assertArrayEquals(expected, reused.transform(input), "diverged on repeat " + repeat);
        }
    }

    @Test
    void rekeyingDoesNotDisturbNoncedTransforms() {
        byte[] input = input();
        ByteEnigma reused = new ByteEnigma(0, 3);
        for (int key : new int[] {5, -5, 77, Integer.MIN_VALUE}) {
            reused.rekey(key);
            assertArrayEquals(new ByteEnigma(key, 3).transform(input, 99L), reused.transform(input, 99L),
                    "nonced transform diverged after rekeying to " + key);
        }
    }
}
