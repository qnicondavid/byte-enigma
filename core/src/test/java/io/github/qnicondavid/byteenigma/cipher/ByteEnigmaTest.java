package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ByteEnigmaTest {

    private ByteEnigma machine(String key) {
        return ByteEnigma.fromPassword(key, 3);
    }

    @Test
    void roundTripAscii() {
        ByteEnigma m = machine("seed-A");
        String msg = "Reciprocity check 123.";
        assertEquals(msg, m.decrypt(m.encrypt(msg)));
    }

    @Test
    void roundTripUnicode() {
        ByteEnigma m = machine("unicode");
        String msg = "cafe € 😀 你好";
        assertEquals(msg, m.decrypt(m.encrypt(msg)));
    }

    @Test
    void roundTripWithNegativeSeedPassword() {
        ByteEnigma m = machine("negativeSeedTest");
        String msg = "The quick brown fox.";
        assertEquals(msg, m.decrypt(m.encrypt(msg)));
    }

    @Test
    void roundTripEmptyString() {
        ByteEnigma m = machine("seed-A");
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
        ByteEnigma m = machine("bytes");
        byte[] input = "binary\0\1\2ÿpayload".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input, m.transform(m.transform(input)));
    }

    @Test
    void noByteEncryptsToItself() {
        ByteEnigma m = machine("no-fixed-point");
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
        ByteEnigma m = machine("buffer");
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
        ByteEnigma m = machine("buffer");
        try {
            m.transform(new byte[10], new byte[5]);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void rotorCountMustBePositive() {
        try {
            new ByteEnigma(123, 0);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void transformIntoBufferReturnsBytesWrittenAndLeavesTailUntouched() {
        ByteEnigma m = machine("buffer");
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
        ByteEnigma m = machine("contract");
        try {
            m.decrypt("###not base64###");
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    void decryptOfForeignCiphertextDoesNotThrow() {
        ByteEnigma m = machine("contract");
        String foreign = java.util.Base64.getEncoder()
                .encodeToString(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xC0});
        assertNotNull(m.decrypt(foreign));
    }

    @Test
    void rotorCountAtCapIsAccepted() {
        ByteEnigma m = new ByteEnigma(1, ByteEnigma.MAX_ROTOR_COUNT);
        assertEquals(ByteEnigma.MAX_ROTOR_COUNT, m.rotors().size());
    }

    @Test
    void rotorCountAboveCapThrows() {
        try {
            new ByteEnigma(1, ByteEnigma.MAX_ROTOR_COUNT + 1);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
