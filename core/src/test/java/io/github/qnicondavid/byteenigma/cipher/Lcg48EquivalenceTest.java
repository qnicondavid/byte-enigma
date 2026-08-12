package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Pins the claim that makes the sweep fast: {@link Lcg48} is not merely a good generator, it is
 * the same generator {@code java.util.Random} is, draw for draw, without the atomic state.
 *
 * <p>If this ever fails, every key in the project changes meaning and the golden vector goes
 * with it.
 */
class Lcg48EquivalenceTest {

    @Test
    void matchesJavaRandomOverTheBoundsTheKeyScheduleUses() {
        int[] seeds = {0, 1, -1, 42, -42, 12345, Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int seed : seeds) {
            Random reference = new Random(seed);
            Lcg48 candidate = new Lcg48(seed);
            for (int draw = 0; draw < 100_000; draw++) {
                int bound = 1 + (draw % 256);
                assertEquals(reference.nextInt(bound), candidate.nextInt(bound),
                        "diverged at seed " + seed + ", draw " + draw + ", bound " + bound);
            }
        }
    }

    @Test
    void matchesJavaRandomOnNonPowerOfTwoBoundsWhereTheRejectionLoopBites() {
        int[] awkwardBounds = {3, 7, 11, 100, 255, 1_000_000_007, Integer.MAX_VALUE};
        for (int bound : awkwardBounds) {
            Random reference = new Random(bound);
            Lcg48 candidate = new Lcg48(bound);
            for (int draw = 0; draw < 200_000; draw++) {
                assertEquals(reference.nextInt(bound), candidate.nextInt(bound),
                        "diverged at bound " + bound + ", draw " + draw);
            }
        }
    }

    @Test
    void reseedingMatchesConstructingAfresh() {
        Lcg48 reused = new Lcg48(0);
        for (int seed = -500; seed <= 500; seed++) {
            reused.setSeed(seed);
            Random reference = new Random(seed);
            for (int draw = 0; draw < 300; draw++) {
                assertEquals(reference.nextInt(256), reused.nextInt(256),
                        "diverged after reseed to " + seed + " at draw " + draw);
            }
        }
    }

    @Test
    void widensIntSeedsWithSignExtensionLikeJavaRandomDoes() {
        Random reference = new Random(-1);
        Lcg48 candidate = new Lcg48(-1);
        for (int draw = 0; draw < 1000; draw++) {
            assertEquals(reference.nextInt(256), candidate.nextInt(256));
        }
    }
}
