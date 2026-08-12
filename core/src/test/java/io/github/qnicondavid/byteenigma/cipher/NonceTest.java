package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The leak the textbook mode has, and the fact that a nonce closes it.
 *
 * <p>These are the tests behind the claim in the README, so they are written to fail loudly if
 * either half of it stops being true.
 */
class NonceTest {

    private static final String FIRST =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR WITHOUT WARNING";
    private static final String SECOND =
            "THE ENEMY FLEET WILL SAIL AT DUSK AND ATTACK THE NORTHERN HARBOUR WITHOUT WARNING";

    private static int agreements(byte[] left, byte[] right) {
        int count = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            if (left[i] == right[i]) {
                count++;
            }
        }
        return count;
    }

    @Test
    void withoutANonceTwoMessagesLeakExactlyWhereTheyAgree() {
        ByteEnigma machine = ByteEnigma.fromPassword("reuse", 3);
        byte[] first = FIRST.getBytes(StandardCharsets.UTF_8);
        byte[] second = SECOND.getBytes(StandardCharsets.UTF_8);

        int plaintextAgreements = agreements(first, second);
        int ciphertextAgreements = agreements(machine.transform(first), machine.transform(second));

        assertTrue(plaintextAgreements > 50,
                "test data should share most of its bytes, shared " + plaintextAgreements);
        assertEquals(plaintextAgreements, ciphertextAgreements,
                "textbook mode should leak agreement positions one for one");
    }

    @Test
    void aNonceRemovesThatCorrespondence() {
        ByteEnigma machine = ByteEnigma.fromPassword("reuse", 3);
        byte[] first = FIRST.getBytes(StandardCharsets.UTF_8);
        byte[] second = SECOND.getBytes(StandardCharsets.UTF_8);

        int plaintextAgreements = agreements(first, second);
        int noncedAgreements = agreements(machine.transform(first, 1L), machine.transform(second, 2L));

        assertTrue(noncedAgreements < plaintextAgreements / 4,
                "nonced ciphertexts still agreed on " + noncedAgreements + " of " + plaintextAgreements
                        + " positions, which is far more than chance");
    }

    @Test
    void reusingANonceReinstatesTheLeak() {
        ByteEnigma machine = ByteEnigma.fromPassword("reuse", 3);
        byte[] first = FIRST.getBytes(StandardCharsets.UTF_8);
        byte[] second = SECOND.getBytes(StandardCharsets.UTF_8);
        assertEquals(agreements(first, second),
                agreements(machine.transform(first, 7L), machine.transform(second, 7L)),
                "a repeated nonce should be exactly as bad as no nonce");
    }

    @Test
    void noncedTransformIsStillItsOwnInverse() {
        ByteEnigma machine = ByteEnigma.fromPassword("reciprocity", 3);
        byte[] input = FIRST.getBytes(StandardCharsets.UTF_8);
        for (long nonce : new long[] {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE}) {
            assertArrayEquals(input, machine.transform(machine.transform(input, nonce), nonce),
                    "not self-inverse at nonce " + nonce);
        }
    }

    @Test
    void noncedTransformStillHasNoFixedPoints() {
        ByteEnigma machine = ByteEnigma.fromPassword("fixed-points", 3);
        byte[] input = new byte[1024];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        byte[] output = machine.transform(input, 12345L);
        for (int i = 0; i < input.length; i++) {
            assertTrue(input[i] != output[i], "byte " + i + " mapped to itself under a nonce");
        }
    }

    @Test
    void differentNoncesUnderOneKeyGiveDifferentCiphertext() {
        ByteEnigma machine = ByteEnigma.fromPassword("nonces", 3);
        byte[] input = FIRST.getBytes(StandardCharsets.UTF_8);
        byte[] one = machine.transform(input, 1L);
        byte[] two = machine.transform(input, 2L);
        assertTrue(agreements(one, two) < input.length / 4,
                "two nonces produced suspiciously similar ciphertext");
    }
}
