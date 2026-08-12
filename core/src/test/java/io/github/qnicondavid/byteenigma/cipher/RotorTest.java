package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One rotor in isolation: the two directions invert, and the offset behaves like an odometer wheel. */
class RotorTest {

    @Test
    void backwardInvertsForwardAtEveryOffset() {
        Rotor rotor = new Rotor(42, 0);
        for (int offset = 0; offset < ByteEnigma.ALPHABET_SIZE; offset++) {
            for (int value = 0; value < ByteEnigma.ALPHABET_SIZE; value++) {
                assertEquals(value, rotor.backward(rotor.forward(value)),
                        "not invertible at offset " + offset + ", value " + value);
            }
            rotor.advance();
        }
    }

    @Test
    void theOffsetWrapsAfterAFullTurn() {
        Rotor rotor = new Rotor(7, 0);
        assertEquals(0, rotor.position());
        for (int i = 0; i < ByteEnigma.ALPHABET_SIZE; i++) {
            rotor.advance();
        }
        assertEquals(0, rotor.position());
    }

    @Test
    void resetReturnsToTheKeyDerivedOffset() {
        Rotor rotor = new Rotor(7, 100);
        rotor.advance();
        rotor.advance();
        rotor.reset();
        assertEquals(100, rotor.position());
    }

    @Test
    void theTurnoverPointStaysInRangeForNegativeSeeds() {
        for (int seed : new int[] {-1, -255, -256, -257, Integer.MIN_VALUE}) {
            int turnover = new Rotor(seed, 0).turnoverPoint();
            assertTrue(turnover >= 0 && turnover < ByteEnigma.ALPHABET_SIZE,
                    "turnover point " + turnover + " out of range at seed " + seed);
        }
    }

    @Test
    void positionAtOverridesTheOffsetWithinRange() {
        Rotor rotor = new Rotor(3, 10);
        rotor.positionAt(300);
        assertEquals(300 & 0xFF, rotor.position());
    }
}
