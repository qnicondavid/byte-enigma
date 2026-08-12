package io.github.qnicondavid.byteenigma.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A candidate holds a copy of the plaintext, not a view onto a buffer someone else is reusing. */
class CandidateTest {

    @Test
    void copiesOutOfTheScratchBufferOnTheWayIn() {
        byte[] scratch = {1, 2, 3, 4, 5, 6, 7, 8};
        Candidate candidate = Candidate.of(42, 1.0, scratch, 4);
        scratch[0] = 99;
        assertArrayEquals(new byte[] {1, 2, 3, 4}, candidate.plaintext());
    }

    @Test
    void handsOutACopyOnTheWayOut() {
        Candidate candidate = Candidate.of(42, 1.0, new byte[] {1, 2, 3, 4}, 4);
        candidate.plaintext()[0] = 99;
        assertEquals(1, candidate.plaintext()[0]);
    }

    @Test
    void equalityLooksAtThePlaintextRatherThanTheArrayIdentity() {
        Candidate left = Candidate.of(1, 2.0, new byte[] {9, 8, 7}, 3);
        Candidate right = Candidate.of(1, 2.0, new byte[] {9, 8, 7}, 3);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
    }

    @Test
    void higherScoresSortLater() {
        Candidate low = Candidate.of(1, -100.0, new byte[] {1}, 1);
        Candidate high = Candidate.of(2, -1.0, new byte[] {1}, 1);
        assertTrue(low.compareTo(high) < 0);
    }

    @Test
    void toStringDoesNotDumpThePlaintext() {
        String rendered = Candidate.of(7, 1.5, "secret".getBytes(), 6).toString();
        assertTrue(rendered.contains("key=7"), rendered);
        assertTrue(!rendered.contains("secret"), rendered);
    }
}
