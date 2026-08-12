package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every component of a machine is seeded from the same key through a different tag.
 *
 * <p>The distinctness cases below hold for any injective mixing function, which the current one is,
 * so they pass today by construction. They are here as a regression guard: replace {@code mix} with
 * something that folds two tags together and they go red immediately.
 *
 * <p>{@link #adjacentKeysDoNotProduceAdjacentTurnoverPoints()} is the case that pins what the
 * MurmurHash3 finaliser is actually for, and it fails without it. Distinctness was never the
 * problem; correlation was. Without the finaliser {@code mix(key, tag)} is
 * {@code key ^ (tag * constant)}, so consecutive keys give consecutive sub-keys, and a rotor's
 * turnover point is {@code floorMod(subKey, 256)}. Neighbouring keys would then produce machines
 * that step over each other in lockstep.
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
    void adjacentKeysDoNotProduceAdjacentTurnoverPoints() {
        int samples = 20_000;
        int adjacent = 0;
        for (int key = -samples / 2; key < samples / 2; key++) {
            int here = Math.floorMod(ByteEnigma.mix(key, 1), 256);
            int next = Math.floorMod(ByteEnigma.mix(key + 1, 1), 256);
            int step = (next - here) & 0xFF;
            if (step == 1 || step == 255) {
                adjacent++;
            }
        }
        // Chance alone puts this near 2/256 of the samples, about 156. Strip the finaliser and
        // mix() becomes an exclusive-or with a constant, which moves the turnover point by one for
        // every second key: half of them.
        assertTrue(adjacent < samples / 32,
                "turnover points track the key: " + adjacent + " of " + samples
                        + " adjacent key pairs moved it by one");
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
