package com.enigma;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EnigmaMachineTest {

    private EnigmaMachine machine(String key) {
        return EnigmaMachine.fromPassword(key, 3);
    }

    @Test
    void roundTripAscii() {
        EnigmaMachine m = machine("seed-A");
        String msg = "Reciprocity check 123.";
        assertEquals(msg, m.decrypt(m.encrypt(msg)));
    }

    @Test
    void roundTripUnicode() {
        EnigmaMachine m = machine("unicode");
        String msg = "cafe € 😀 你好";
        assertEquals(msg, m.decrypt(m.encrypt(msg)));
    }

    @Test
    void roundTripWithNegativeSeedPassword() {
        EnigmaMachine m = machine("negativeSeedTest");
        String msg = "The quick brown fox.";
        assertEquals(msg, m.decrypt(m.encrypt(msg)));
    }

    @Test
    void roundTripEmptyString() {
        EnigmaMachine m = machine("seed-A");
        assertEquals("", m.decrypt(m.encrypt("")));
    }

    @Test
    void sameKeyIsDeterministic() {
        String msg = "Determinism.";
        assertEquals(machine("k").encrypt(msg), machine("k").encrypt(msg));
    }

    @Test
    void differentKeysProduceDifferentCiphertext() {
        String msg = "Determinism.";
        assertNotEquals(machine("k1").encrypt(msg), machine("k2").encrypt(msg));
    }

    @Test
    void transformIsItsOwnInverse() {
        EnigmaMachine m = machine("bytes");
        byte[] input = "binary\0\1\2ÿpayload".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input, m.transform(m.transform(input)));
    }

    @Test
    void noByteEncryptsToItself() {
        EnigmaMachine m = machine("no-fixed-point");
        byte[] input = new byte[4096];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (i & 0xFF);
        }
        byte[] output = m.transform(input);
        for (int i = 0; i < input.length; i++) {
            assertNotEquals(input[i], output[i], "byte at index " + i + " mapped to itself");
        }
    }

    @Test
    void transformIntoBufferMatchesAllocating() {
        EnigmaMachine m = machine("buffer");
        byte[] input = "reuse this buffer".getBytes(StandardCharsets.UTF_8);
        byte[] expected = m.transform(input);
        byte[] output = new byte[input.length + 32];
        m.transform(input, output);
        for (int i = 0; i < input.length; i++) {
            assertEquals(expected[i], output[i]);
        }
    }

    @Test
    void transformRejectsUndersizedBuffer() {
        EnigmaMachine m = machine("buffer");
        try {
            m.transform(new byte[10], new byte[5]);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void rotorCountMustBePositive() {
        try {
            new EnigmaMachine(123, 0);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void transformIntoBufferReturnsBytesWrittenAndLeavesTailUntouched() {
        EnigmaMachine m = machine("buffer");
        byte[] input = "reuse this buffer".getBytes(StandardCharsets.UTF_8);
        byte[] output = new byte[input.length + 8];
        java.util.Arrays.fill(output, (byte) 0x7F);
        int written = m.transform(input, output);
        assertEquals(input.length, written);
        for (int i = input.length; i < output.length; i++) {
            assertEquals((byte) 0x7F, output[i], "tail byte " + i + " must be left untouched");
        }
    }

    @Test
    void decryptRejectsMalformedBase64() {
        EnigmaMachine m = machine("contract");
        try {
            m.decrypt("###not base64###");
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void decryptOfForeignCiphertextDoesNotThrow() {
        EnigmaMachine m = machine("contract");
        String foreign = java.util.Base64.getEncoder()
                .encodeToString(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xC0});
        assertNotNull(m.decrypt(foreign));
    }

    @Test
    void rotorCountAtCapIsAccepted() {
        EnigmaMachine m = new EnigmaMachine(1, EnigmaMachine.MAX_ROTOR_COUNT);
        assertEquals(EnigmaMachine.MAX_ROTOR_COUNT, m.rotors().size());
    }

    @Test
    void rotorCountAboveCapThrows() {
        try {
            new EnigmaMachine(1, EnigmaMachine.MAX_ROTOR_COUNT + 1);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
