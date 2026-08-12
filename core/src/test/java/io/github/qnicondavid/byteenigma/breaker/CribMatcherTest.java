package io.github.qnicondavid.byteenigma.breaker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CribMatcherTest {

    private static final String TEXT = "MEET AT THE OLD MILL AT MIDNIGHT AND BRING THE DOCUMENTS";
    private static final String CRIB = "OLD MILL";
    private static final int SECRET = 1_234_567;

    private static byte[] ciphertext(int key) {
        return new ByteEnigma(key, 3).transform(TEXT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theRightKeyAtTheRightOffsetIsAccepted() {
        byte[] ciphertext = ciphertext(SECRET);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        int offset = TEXT.indexOf(CRIB);

        Candidate hit = new CribMatcher(crib, offset)
                .evaluate(SECRET, new ByteEnigma(0, 3), ciphertext, new byte[ciphertext.length]);

        assertNotNull(hit);
        assertEquals(SECRET, hit.key());
        assertArrayEquals(TEXT.getBytes(StandardCharsets.UTF_8), hit.plaintext(),
                "a hit should carry the whole message, not just the crib");
    }

    @Test
    void aWrongKeyIsRejected() {
        byte[] ciphertext = ciphertext(SECRET);
        CribMatcher matcher = new CribMatcher(CRIB.getBytes(StandardCharsets.UTF_8), TEXT.indexOf(CRIB));
        for (int delta : new int[] {1, -1, 1000, -1000}) {
            assertNull(matcher.evaluate(SECRET + delta, new ByteEnigma(0, 3),
                    ciphertext, new byte[ciphertext.length]), "accepted key " + (SECRET + delta));
        }
    }

    @Test
    void theRightKeyAtTheWrongOffsetIsRejected() {
        byte[] ciphertext = ciphertext(SECRET);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        int wrongOffset = TEXT.indexOf(CRIB) + 1;
        assertNull(new CribMatcher(crib, wrongOffset)
                .evaluate(SECRET, new ByteEnigma(0, 3), ciphertext, new byte[ciphertext.length]));
    }

    @Test
    void theTrueOffsetIsAlwaysAdmissible() {
        byte[] ciphertext = ciphertext(SECRET);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        int offset = TEXT.indexOf(CRIB);
        assertTrue(CribMatcher.offsetAdmissible(ciphertext, crib, offset));
        assertTrue(CribMatcher.admissibleOffsets(ciphertext, crib).contains(offset));
    }

    @Test
    void theNoFixedPointRuleThrowsAwayMostPositionsForFree() {
        byte[] ciphertext = ciphertext(SECRET);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        double eliminated = CribMatcher.eliminationRate(ciphertext, crib);
        assertTrue(eliminated > 0.05,
                "expected the reciprocity discount to eliminate positions, got " + eliminated);
        assertTrue(eliminated < 1.0, "it cannot eliminate the true offset");
    }

    @Test
    void offsetsOutsideTheMessageAreNeverAdmissible() {
        byte[] ciphertext = ciphertext(SECRET);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        assertTrue(!CribMatcher.offsetAdmissible(ciphertext, crib, -1));
        assertTrue(!CribMatcher.offsetAdmissible(ciphertext, crib, ciphertext.length));
        assertTrue(!CribMatcher.offsetAdmissible(ciphertext, new byte[0], 0));
    }

    @Test
    void aCribRunningOffTheEndRejectsEveryKey() {
        byte[] ciphertext = ciphertext(SECRET);
        CribMatcher matcher = new CribMatcher(CRIB.getBytes(StandardCharsets.UTF_8), ciphertext.length - 2);
        assertNull(matcher.evaluate(SECRET, new ByteEnigma(0, 3), ciphertext, new byte[ciphertext.length]));
    }

    @Test
    void anEmptyCribIsRejectedAtConstruction() {
        try {
            new CribMatcher(new byte[0], 0);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
    }

    @Test
    void theCribIsCopiedSoLaterMutationCannotChangeTheAttack() {
        byte[] ciphertext = ciphertext(SECRET);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        CribMatcher matcher = new CribMatcher(crib, TEXT.indexOf(CRIB));
        crib[0] = 'X';
        assertNotNull(matcher.evaluate(SECRET, new ByteEnigma(0, 3), ciphertext, new byte[ciphertext.length]));
    }
}
