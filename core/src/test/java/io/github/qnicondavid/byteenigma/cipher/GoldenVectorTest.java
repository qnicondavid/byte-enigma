package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoldenVectorTest {

    @Test
    void encryptMatchesGoldenVector() {
        ByteEnigma machine = ByteEnigma.fromPassword("golden-key", 3);
        String message = "The quick brown fox jumps over the lazy dog.";
        String expected = "pF9Bz42TulETis9AXWT0UW+i790j3qAqybFjqHbHFQvsWeNJnpyv/5Y47fs=";
        assertEquals(expected, machine.encrypt(message));
    }
}
