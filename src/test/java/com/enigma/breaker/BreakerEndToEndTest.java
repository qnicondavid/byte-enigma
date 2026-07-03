package com.enigma.breaker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enigma.EnigmaMachine;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BreakerEndToEndTest {

    private static final int ROTOR_COUNT = 3;
    private static final int SECRET_SEED = 4_242_424;
    private static final long WINDOW_RADIUS = 1_500L;
    private static final String PLAINTEXT =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR WITHOUT WARNING";
    private static final String CRIB_TEXT = "SOUTHERN HARBOUR";

    @Test
    void cribModeRecoversSecretSeedAndPlaintextColdInBoundedWindow() {
        byte[] plaintext = PLAINTEXT.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new EnigmaMachine(SECRET_SEED, ROTOR_COUNT).transform(plaintext);

        byte[] crib = CRIB_TEXT.getBytes(StandardCharsets.UTF_8);
        int cribOffset = PLAINTEXT.indexOf(CRIB_TEXT);
        assertTrue(crib.length >= 6);
        assertTrue(CribMatcher.offsetAdmissible(ciphertext, crib, cribOffset));

        SeedSweep sweep = new SeedSweep(ROTOR_COUNT, 1);
        SeedSweep.SweepResult result = sweep.sweep(
                SECRET_SEED - WINDOW_RADIUS,
                SECRET_SEED + WINDOW_RADIUS,
                ciphertext,
                new CribMatcher(crib, cribOffset));

        Candidate top = result.top();
        assertNotNull(top);
        assertEquals(SECRET_SEED, top.seed());
        assertArrayEquals(plaintext, top.plaintext());
        assertTrue(result.keysPerSecond() > 0.0);
    }

    @Test
    void quadgramModeRecoversSecretSeedAndPlaintextColdInBoundedWindow() {
        byte[] plaintext = PLAINTEXT.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new EnigmaMachine(SECRET_SEED, ROTOR_COUNT).transform(plaintext);

        QuadgramSearch search = new QuadgramSearch(QuadgramScorer.fromResource());
        SeedSweep sweep = new SeedSweep(ROTOR_COUNT, 1);
        SeedSweep.SweepResult result = sweep.sweep(
                SECRET_SEED - WINDOW_RADIUS,
                SECRET_SEED + WINDOW_RADIUS,
                ciphertext,
                search);

        Candidate top = result.top();
        assertNotNull(top);
        assertEquals(SECRET_SEED, top.seed());
        assertArrayEquals(plaintext, top.plaintext());
        assertTrue(result.keysPerSecond() > 0.0);
    }
}
