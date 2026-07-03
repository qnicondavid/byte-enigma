package com.enigma.breaker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enigma.EnigmaMachine;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CribMatcherTest {

    @Test
    void trueOffsetIsAdmissibleAndTrueSeedRecovers() {
        int secret = 1234567;
        String text = "MEET AT THE OLD MILL AT MIDNIGHT AND BRING THE DOCUMENTS";
        byte[] plaintext = text.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new EnigmaMachine(secret, 3).transform(plaintext);

        String cribText = "OLD MILL";
        int offset = text.indexOf(cribText);
        byte[] crib = cribText.getBytes(StandardCharsets.UTF_8);

        assertTrue(CribMatcher.offsetAdmissible(ciphertext, crib, offset));
        assertTrue(CribMatcher.admissibleOffsets(ciphertext, crib).contains(offset));

        CribMatcher matcher = new CribMatcher(crib, offset);
        byte[] out = new byte[ciphertext.length];
        Candidate hit = matcher.evaluate(secret, new EnigmaMachine(secret, 3), ciphertext, out);

        assertNotNull(hit);
        assertEquals(secret, hit.seed());
        assertArrayEquals(plaintext, hit.plaintext());
    }

    @Test
    void wrongSeedIsRejected() {
        int secret = 42;
        String text = "ATTACK AT DAWN ON THE NORTHERN RIDGE";
        byte[] ciphertext = new EnigmaMachine(secret, 3).transform(text.getBytes(StandardCharsets.UTF_8));

        String cribText = "DAWN";
        int offset = text.indexOf(cribText);
        CribMatcher matcher = new CribMatcher(cribText.getBytes(StandardCharsets.UTF_8), offset);

        byte[] out = new byte[ciphertext.length];
        assertNull(matcher.evaluate(secret + 1, new EnigmaMachine(secret + 1, 3), ciphertext, out));
    }
}
