package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The passphrase derivation used to be {@code String.hashCode}, which collides on structure
 * rather than on chance: {@code "Aa"} and {@code "BB"} share a hash, and so did their keys, and
 * so did their ciphertexts. FNV-1a over the UTF-8 bytes separates them.
 *
 * <p>This buys tidiness, not security. The result is still 32 bits wide with no salt and no work
 * factor, and no test here should be read as claiming otherwise.
 */
class PassphraseKeyTest {

    private static final String MESSAGE = "The quick brown fox jumps over the lazy dog.";

    private static final String[][] HASH_CODE_COLLISIONS = {
        {"Aa", "BB"}, {"Ba", "CB"}, {"Ca", "DB"}, {"Ab", "BC"}
    };

    @Test
    void passphrasesThatShareAJavaHashCodeGetDifferentKeys() {
        for (String[] pair : HASH_CODE_COLLISIONS) {
            assertEquals(pair[0].hashCode(), pair[1].hashCode(),
                    "test data is stale: " + pair[0] + " and " + pair[1] + " no longer collide");
            assertNotEquals(ByteEnigma.seedFromPassword(pair[0]), ByteEnigma.seedFromPassword(pair[1]),
                    "keys collided for " + pair[0] + "/" + pair[1]);
        }
    }

    @Test
    void passphrasesThatShareAJavaHashCodeGetDifferentCiphertext() {
        byte[] message = MESSAGE.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (String[] pair : HASH_CODE_COLLISIONS) {
            assertNotEquals(
                    Arrays.toString(ByteEnigma.fromPassword(pair[0], 3).transform(message)),
                    Arrays.toString(ByteEnigma.fromPassword(pair[1], 3).transform(message)),
                    "ciphertext collided for " + pair[0] + "/" + pair[1]);
        }
    }

    @Test
    void everyTwoLetterPassphraseGetsItsOwnKey() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Set<Integer> keys = new HashSet<>();
        int total = 0;
        for (int i = 0; i < alphabet.length(); i++) {
            for (int j = 0; j < alphabet.length(); j++) {
                keys.add(ByteEnigma.seedFromPassword("" + alphabet.charAt(i) + alphabet.charAt(j)));
                total++;
            }
        }
        assertEquals(2704, total);
        assertEquals(total, keys.size(), "two-letter passphrases collided");
    }

    @Test
    void theDerivationIsStableAcrossCalls() {
        assertEquals(ByteEnigma.seedFromPassword("stability"), ByteEnigma.seedFromPassword("stability"));
    }
}
