package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class InvolutionTest {

    @Test
    void applyingTwiceIsIdentity() {
        Involution involution = new Involution(99);
        for (int c = 0; c < 256; c++) {
            assertEquals(c, involution.apply(involution.apply(c)));
        }
    }

    @Test
    void hasNoFixedPoint() {
        Involution involution = new Involution(99);
        for (int c = 0; c < 256; c++) {
            assertNotEquals(c, involution.apply(c));
        }
    }

    @Test
    void everyValueMaps() {
        Involution involution = new Involution(~12345);
        boolean[] seen = new boolean[256];
        for (int c = 0; c < 256; c++) {
            seen[involution.apply(c)] = true;
        }
        for (int i = 0; i < 256; i++) {
            assertEquals(true, seen[i], "value " + i + " is not in the image");
        }
    }
}
