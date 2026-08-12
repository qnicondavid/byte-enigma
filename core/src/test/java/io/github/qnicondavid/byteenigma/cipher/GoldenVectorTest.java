package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * One frozen ciphertext, checked byte for byte.
 *
 * <p>Every refactor in this project has been measured against this vector: the alphabet, the key
 * schedule, the stepping and the generator have all been rewritten underneath it and it has not
 * moved. It is the reason the rewrite from {@code java.util.Random} to {@link Lcg48} could be
 * called an optimisation rather than a change.
 *
 * <p>The value below has been rebased exactly twice, both times deliberately and both times
 * recorded in the changelog: once when the passphrase derivation moved off {@code String.hashCode},
 * and once when the component sub-keys were decorrelated.
 */
class GoldenVectorTest {

    private static final String PASSPHRASE = "golden-key";
    private static final String MESSAGE = "The quick brown fox jumps over the lazy dog.";
    private static final String EXPECTED = "pF9Bz42TulETis9AXWT0UW+i790j3qAqybFjqHbHFQvsWeNJnpyv/5Y47fs=";

    @Test
    void textbookTransformMatchesTheFrozenVector() {
        ByteEnigma machine = ByteEnigma.fromPassword(PASSPHRASE, 3);
        byte[] ciphertext = machine.transform(MESSAGE.getBytes(StandardCharsets.UTF_8));
        assertEquals(EXPECTED, Base64.getEncoder().encodeToString(ciphertext));
    }

    @Test
    void theFrozenVectorDecryptsBackToTheMessage() {
        ByteEnigma machine = ByteEnigma.fromPassword(PASSPHRASE, 3);
        byte[] plaintext = machine.transform(Base64.getDecoder().decode(EXPECTED));
        assertEquals(MESSAGE, new String(plaintext, StandardCharsets.UTF_8));
    }
}
