package io.github.qnicondavid.byteenigma.breaker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SeedEvaluator;
import io.github.qnicondavid.byteenigma.search.SeedSweep;
import io.github.qnicondavid.byteenigma.search.SweepResult;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Cold recovery: the search is given the ciphertext and a range, and nothing else. The window is
 * bounded so the suite stays fast; {@code docs/keyspace-sweep.md} reports the unbounded run.
 */
class BreakerEndToEndTest {

    private static final int ROTORS = 3;
    private static final int SECRET = 4_242_424;
    private static final long RADIUS = 1_500L;
    private static final String TEXT =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR WITHOUT WARNING";
    private static final String CRIB = "SOUTHERN HARBOUR";

    private static SweepResult recover(byte[] ciphertext, SeedEvaluator<ByteEnigma> evaluator) {
        return new SeedSweep<>(() -> new ByteEnigma(0, ROTORS), 1)
                .sweep(SECRET - RADIUS, SECRET + RADIUS, ciphertext, evaluator);
    }

    private static void assertRecovered(SweepResult result, byte[] expected) {
        Candidate top = result.top();
        assertNotNull(top, "nothing recovered");
        assertEquals(SECRET, top.key());
        assertArrayEquals(expected, top.plaintext());
        assertTrue(result.keysPerSecond() > 0.0);
    }

    @Test
    void theCribRouteRecoversKeyAndPlaintext() {
        byte[] plaintext = TEXT.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new ByteEnigma(SECRET, ROTORS).transform(plaintext);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        assertTrue(CribMatcher.offsetAdmissible(ciphertext, crib, TEXT.indexOf(CRIB)));
        assertRecovered(recover(ciphertext, new CribMatcher(crib, TEXT.indexOf(CRIB))), plaintext);
    }

    @Test
    void theLanguageRouteRecoversKeyAndPlaintextWithNoCribAtAll() {
        byte[] plaintext = TEXT.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new ByteEnigma(SECRET, ROTORS).transform(plaintext);
        assertRecovered(recover(ciphertext, QuadgramSearch.usingBundledTable()), plaintext);
    }

    @Test
    void parallelRecoveryAgreesWithSerialRecovery() {
        byte[] plaintext = TEXT.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new ByteEnigma(SECRET, ROTORS).transform(plaintext);
        SweepResult parallel = new SeedSweep<>(() -> new ByteEnigma(0, ROTORS), 1)
                .sweepParallel(SECRET - RADIUS, SECRET + RADIUS, ciphertext,
                        QuadgramSearch.usingBundledTable(), 4);
        assertRecovered(parallel, plaintext);
    }

    @Test
    void theTrueKeyOutscoresEveryOtherKeyByAWideMargin() {
        byte[] plaintext = TEXT.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new ByteEnigma(SECRET, ROTORS).transform(plaintext);
        SweepResult result = new SeedSweep<>(() -> new ByteEnigma(0, ROTORS), 2)
                .sweep(SECRET - RADIUS, SECRET + RADIUS, ciphertext, QuadgramSearch.usingBundledTable());
        double margin = result.best().get(0).score() - result.best().get(1).score();
        assertTrue(margin > 20.0,
                "the true key should win by a wide margin, but only led by " + margin);
    }
}
