package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The reflector's two properties, which the whole design leans on. */
class InvolutionTest {

    @Test
    void applyingItTwiceIsTheIdentity() {
        Involution involution = new Involution(99);
        for (int value = 0; value < ByteEnigma.ALPHABET_SIZE; value++) {
            assertEquals(value, involution.apply(involution.apply(value)));
        }
    }

    @Test
    void nothingMapsToItself() {
        for (int seed : new int[] {99, 0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            Involution involution = new Involution(seed);
            for (int value = 0; value < ByteEnigma.ALPHABET_SIZE; value++) {
                assertNotEquals(value, involution.apply(value), "fixed point at seed " + seed);
            }
        }
    }

    @Test
    void everyValueIsInTheImage() {
        Involution involution = new Involution(~12345);
        boolean[] seen = new boolean[ByteEnigma.ALPHABET_SIZE];
        for (int value = 0; value < ByteEnigma.ALPHABET_SIZE; value++) {
            seen[involution.apply(value)] = true;
        }
        for (int value = 0; value < ByteEnigma.ALPHABET_SIZE; value++) {
            assertTrue(seen[value], "value " + value + " is not in the image");
        }
    }

    @Test
    void reseedingInPlaceMatchesBuildingAfresh() {
        Involution reused = new Involution(0);
        for (int seed = -200; seed <= 200; seed++) {
            reused.reseed(seed);
            Involution fresh = new Involution(seed);
            for (int value = 0; value < ByteEnigma.ALPHABET_SIZE; value++) {
                assertEquals(fresh.apply(value), reused.apply(value), "diverged at seed " + seed);
            }
        }
    }
}
