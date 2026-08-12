package io.github.qnicondavid.byteenigma.breaker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QuadgramSearchTest {

    @Test
    void scoreRanksTrueSeedTopInBoundedRange() {
        int secret = 5000;
        String text = "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR WITHOUT WARNING";
        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new ByteEnigma(secret, 3).transform(plaintext);

        QuadgramSearch search = new QuadgramSearch(QuadgramScorer.fromResource());
        SeedSweep sweep = new SeedSweep(3, 1);
        SeedSweep.SweepResult result = sweep.sweep(secret - 300L, secret + 300L, ciphertext, search);

        Candidate top = result.top();
        assertNotNull(top);
        assertEquals(secret, top.seed());
        assertArrayEquals(plaintext, top.plaintext());
    }
}
