package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RotorTest {

    @Test
    void decryptInvertsEncryptAtEveryPosition() {
        Rotor rotor = new Rotor(42, 0);
        for (int pos = 0; pos < 256; pos++) {
            for (int c = 0; c < 256; c++) {
                assertEquals(c, rotor.decrypt(rotor.encrypt(c)));
            }
            rotor.rotate();
        }
    }

    @Test
    void rotateWrapsAfter256Steps() {
        Rotor rotor = new Rotor(7, 0);
        assertEquals(0, rotor.position());
        for (int i = 0; i < 256; i++) {
            rotor.rotate();
        }
        assertEquals(0, rotor.position());
    }

    @Test
    void resetReturnsToInitialPosition() {
        Rotor rotor = new Rotor(7, 100);
        rotor.rotate();
        rotor.rotate();
        rotor.reset();
        assertEquals(100, rotor.position());
    }

    @Test
    void turnoverPointStaysInRangeForNegativeSeed() {
        Rotor rotor = new Rotor("negativeSeedTest".hashCode(), 0);
        int tp = rotor.turnoverPoint();
        assertTrue(tp >= 0 && tp < 256, "turnover point out of range: " + tp);
    }
}
