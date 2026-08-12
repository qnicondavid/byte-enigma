package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every component of a machine is seeded from the same key through a different tag. If two tags
 * ever produced the same sub-key, two components would be wired identically and the machine
 * would quietly lose strength it looks like it has.
 *
 * <p>Before the MurmurHash3 finaliser was applied, tags near zero produced sub-keys near each
 * other, and at small keys some collided outright.
 */
class SubKeyDistinctnessTest {

    private static final int MAX_ROTOR_COUNT = 8;

    private static int[] keys() {
        int[] keys = new int[2001 + 8];
        int index = 0;
        for (int key = -1000; key <= 1000; key++) {
            keys[index++] = key;
        }
        int[] extra = {
            0, -1, -2, -3, Integer.MIN_VALUE, Integer.MAX_VALUE,
            Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1
        };
        System.arraycopy(extra, 0, keys, index, extra.length);
        return keys;
    }

    @Test
    void everyComponentGetsItsOwnSubKey() {
        for (int key : keys()) {
            for (int rotorCount = 1; rotorCount <= MAX_ROTOR_COUNT; rotorCount++) {
                Set<Integer> seen = new HashSet<>();
                assertTrue(seen.add(ByteEnigma.mix(key, 0)),
                        "offset generator collided at key " + key);
                for (int rotor = 1; rotor <= rotorCount; rotor++) {
                    assertTrue(seen.add(ByteEnigma.mix(key, rotor)),
                            "rotor " + rotor + " collided at key " + key);
                }
                assertTrue(seen.add(ByteEnigma.mix(key, -1)), "reflector collided at key " + key);
                assertTrue(seen.add(ByteEnigma.mix(key, -2)), "plugboard collided at key " + key);
                assertEquals(rotorCount + 3, seen.size(),
                        "sub-keys collided at key " + key + ", rotors " + rotorCount);
            }
        }
    }

    @Test
    void theFirstRotorNeverCollidesWithTheReflector() {
        for (int key : keys()) {
            assertNotEquals(ByteEnigma.mix(key, 1), ByteEnigma.mix(key, -1), "collided at key " + key);
        }
    }

    @Test
    void nonceMixingSeparatesAdjacentNoncesUnderOneKey() {
        Set<Long> seen = new HashSet<>();
        for (long nonce = -500; nonce <= 500; nonce++) {
            assertTrue(seen.add(ByteEnigma.mix64(4242, nonce)), "nonce " + nonce + " collided");
        }
    }

    @Test
    void nonceMixingSeparatesAdjacentKeysUnderOneNonce() {
        Set<Long> seen = new HashSet<>();
        for (int key = -500; key <= 500; key++) {
            assertTrue(seen.add(ByteEnigma.mix64(key, 7L)), "key " + key + " collided");
        }
    }
}
