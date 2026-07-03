package com.enigma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GoldenVectorTest {

    @Test
    void encryptMatchesGoldenVector() {
        EnigmaMachine machine = EnigmaMachine.fromPassword("golden-key", 3);
        String message = "The quick brown fox jumps over the lazy dog.";
        String expected = "ZwfB44pl/TjFYnP+8pY9RSkXh5sTKr3b2NYZ6CLDF5Kx7W0J5+0iKeB/Af8=";
        assertEquals(expected, machine.encrypt(message));
    }
}
