package io.github.qnicondavid.byteenigma.breaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The scorer's job is to prefer English. Its other job, which is easier to get wrong, is to
 * charge every candidate the same number of windows: the score is a sum of negative terms, so
 * any candidate that gets scored over fewer of them wins on nothing.
 */
class QuadgramScorerTest {

    private static final QuadgramScorer SCORER = QuadgramScorer.fromResource();

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void englishScoresFarAboveNoise() {
        byte[] english = bytes("THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR");
        byte[] noise = new byte[english.length];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = (byte) ((i * 137 + 41) & 0xFF);
        }
        assertTrue(SCORER.score(english, english.length) > SCORER.score(noise, noise.length),
                "English did not outscore noise");
    }

    @Test
    void everyInputOfEqualLengthIsScoredOverEqualWindows() {
        byte[] english = bytes("THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARB");
        int length = english.length;
        byte[] multiByteUtf8 = new byte[length];
        byte[] highBytes = new byte[length];
        java.util.Arrays.fill(highBytes, (byte) 0xFF);
        byte[] snowman = bytes("☃");
        for (int i = 0; i < length; i++) {
            multiByteUtf8[i] = snowman[i % snowman.length];
        }

        double floorTotal = SCORER.floorLogProbability() * (length - 3);
        assertEquals(floorTotal, SCORER.score(highBytes, length), 1e-9,
                "high bytes should sit exactly on the floor");
        assertEquals(floorTotal, SCORER.score(multiByteUtf8, length), 1e-9,
                "valid multi-byte UTF-8 must not buy a shorter window count");
        assertTrue(SCORER.score(english, length) > floorTotal,
                "English of the same length should score above the floor, not merely differently");
    }

    @Test
    void caseDoesNotChangeTheScore() {
        byte[] upper = bytes("THEQUICKBROWNFOXJUMPSOVERTHELAZYDOG");
        byte[] lower = bytes("thequickbrownfoxjumpsoverthelazydog");
        assertEquals(SCORER.score(upper, upper.length), SCORER.score(lower, lower.length), 1e-9);
    }

    @Test
    void windowsThatStraddleASpaceAreChargedTheFloor() {
        byte[] spaced = bytes("AB CD");
        assertEquals(SCORER.floorLogProbability() * 2, SCORER.score(spaced, spaced.length), 1e-9);
    }

    @Test
    void inputsShorterThanOneWindowStillReturnSomething() {
        for (String text : new String[] {"", "A", "AB", "ABC"}) {
            byte[] data = bytes(text);
            assertTrue(SCORER.score(data, data.length) <= 0.0, "short input scored above zero");
        }
    }

    @Test
    void meanScoreIsTheSumDividedByTheWindowCount() {
        byte[] text = bytes("THEQUICKBROWNFOX");
        assertEquals(SCORER.score(text, text.length) / (text.length - 3),
                SCORER.meanScore(text, text.length), 1e-9);
    }

    @Test
    void onlyPartOfTheBufferIsScoredWhenLengthSaysSo() {
        byte[] buffer = bytes("THEQUICKBROWNFOX................");
        assertEquals(SCORER.score(bytes("THEQUICKBROWNFOX"), 16), SCORER.score(buffer, 16), 1e-9);
    }

    @Test
    void theBundledTableCarriesAPositiveTotal() {
        assertTrue(SCORER.totalQuadgrams() > 0, "table total should be positive");
        assertTrue(SCORER.floorLogProbability() < 0, "the floor should be a negative log-probability");
    }

    @Test
    void rejectsCountsThatDoNotFillTheTable() {
        try {
            QuadgramScorer.fromCounts(new int[10], 100L);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("entries"));
        }
    }
}
