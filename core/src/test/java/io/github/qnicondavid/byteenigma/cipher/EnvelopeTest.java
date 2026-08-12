package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Framing: the nonce goes on the front, in the clear, and comes back off again. */
class EnvelopeTest {

    private static final byte[] MESSAGE = "sealed for transport".getBytes(StandardCharsets.UTF_8);

    @Test
    void sealingThenOpeningReturnsTheMessage() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        assertArrayEquals(MESSAGE, Envelope.open(machine, Envelope.seal(machine, MESSAGE)));
    }

    @Test
    void sealingIsLongerThanThePlaintextByExactlyTheNonce() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        assertEquals(MESSAGE.length + Envelope.NONCE_BYTES, Envelope.seal(machine, MESSAGE).length);
    }

    @Test
    void theNonceSurvivesTheRoundTripUnchanged() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        for (long nonce : new long[] {0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 1234567890123L}) {
            byte[] sealed = Envelope.seal(machine, MESSAGE, nonce);
            assertEquals(nonce, Envelope.nonceOf(sealed), "nonce mangled in transit");
            assertArrayEquals(MESSAGE, Envelope.open(machine, sealed));
        }
    }

    @Test
    void sealingTwiceProducesDifferentBytes() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        assertTrue(!java.util.Arrays.equals(Envelope.seal(machine, MESSAGE), Envelope.seal(machine, MESSAGE)),
                "two seals of one message under one key should differ");
    }

    @Test
    void freshNoncesDoNotRepeatOverAThousandSeals() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        Set<Long> nonces = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            nonces.add(Envelope.nonceOf(Envelope.seal(machine, MESSAGE)));
        }
        assertEquals(1000, nonces.size(), "nonce source repeated itself");
    }

    @Test
    void base64FramingRoundTrips() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        assertArrayEquals(MESSAGE,
                Envelope.openFromBase64(machine, Envelope.sealToBase64(machine, MESSAGE)));
    }

    @Test
    void anEmptyMessageStillCarriesItsNonce() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        byte[] sealed = Envelope.seal(machine, new byte[0]);
        assertEquals(Envelope.NONCE_BYTES, sealed.length);
        assertEquals(0, Envelope.open(machine, sealed).length);
    }

    @Test
    void tooShortToBeSealedIsRejected() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        try {
            Envelope.open(machine, new byte[Envelope.NONCE_BYTES - 1]);
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("at least"));
        }
    }

    @Test
    void thereIsNoAuthentication() {
        ByteEnigma machine = ByteEnigma.fromPassword("envelope", 3);
        byte[] sealed = Envelope.seal(machine, MESSAGE, 5L);
        sealed[Envelope.NONCE_BYTES] ^= 0x01;
        byte[] opened = Envelope.open(machine, sealed);
        assertEquals(MESSAGE.length, opened.length);
        assertTrue(!java.util.Arrays.equals(MESSAGE, opened),
                "a flipped bit should change the plaintext");
    }
}
