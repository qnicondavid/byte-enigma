package io.github.qnicondavid.byteenigma.cipher;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Message framing that carries a nonce alongside the ciphertext.
 *
 * <p>{@link ByteEnigma#transform(byte[], byte[])} derives its rotor offsets from the key alone,
 * so two messages under one key are enciphered by the same sequence of permutations. Wherever
 * the plaintexts agree, the ciphertexts agree. An envelope draws a fresh nonce per message,
 * derives the offsets from key and nonce together, and ships the nonce in the clear ahead of
 * the ciphertext so the recipient can reproduce them.
 *
 * <h2>What this fixes and what it does not</h2>
 *
 * <p>It fixes keystream reuse. That is the whole of it.
 *
 * <ul>
 *   <li>The key is still 32 bits. A sweep of the entire keyspace takes a few hours on a laptop,
 *       and the nonce does not slow it down by one key, because the nonce travels in the clear
 *       and is not secret.</li>
 *   <li>There is no authentication. Anyone can flip bits in transit and the recipient will
 *       decrypt the result without complaint.</li>
 *   <li>Nonces are drawn at random from 64 bits, so a key that seals more than a few billion
 *       messages will eventually repeat one and reintroduce the leak.</li>
 * </ul>
 *
 * <p>The point of this class is to show what the fix costs and where it stops, not to make the
 * cipher fit for use.
 */
public final class Envelope {

    /** Width of the nonce prefix, big-endian, ahead of the ciphertext. */
    public static final int NONCE_BYTES = Long.BYTES;

    private static final SecureRandom NONCE_SOURCE = new SecureRandom();

    private Envelope() {
    }

    /** Seals with a freshly drawn nonce. */
    public static byte[] seal(ByteEnigma machine, byte[] plaintext) {
        return seal(machine, plaintext, NONCE_SOURCE.nextLong());
    }

    /**
     * Seals with a caller-chosen nonce, for tests and for reproducing a transcript.
     *
     * <p>Reusing a nonce under one key puts back exactly the leak this class exists to remove.
     */
    public static byte[] seal(ByteEnigma machine, byte[] plaintext, long nonce) {
        byte[] sealed = new byte[NONCE_BYTES + plaintext.length];
        writeNonce(nonce, sealed);
        byte[] body = machine.transform(plaintext, nonce);
        System.arraycopy(body, 0, sealed, NONCE_BYTES, body.length);
        return sealed;
    }

    /** Reads the nonce off the front and undoes the transform. */
    public static byte[] open(ByteEnigma machine, byte[] sealed) {
        if (sealed.length < NONCE_BYTES) {
            throw new IllegalArgumentException(
                    "sealed message needs at least " + NONCE_BYTES + " bytes but had " + sealed.length);
        }
        long nonce = readNonce(sealed);
        byte[] body = new byte[sealed.length - NONCE_BYTES];
        System.arraycopy(sealed, NONCE_BYTES, body, 0, body.length);
        return machine.transform(body, nonce);
    }

    /** Seals and encodes for transport as text. */
    public static String sealToBase64(ByteEnigma machine, byte[] plaintext) {
        return Base64.getEncoder().encodeToString(seal(machine, plaintext));
    }

    /** Decodes and opens. Throws {@link IllegalArgumentException} on input that is not Base64. */
    public static byte[] openFromBase64(ByteEnigma machine, String sealed) {
        return open(machine, Base64.getDecoder().decode(sealed));
    }

    /** The nonce a sealed message is carrying, without opening it. */
    public static long nonceOf(byte[] sealed) {
        if (sealed.length < NONCE_BYTES) {
            throw new IllegalArgumentException(
                    "sealed message needs at least " + NONCE_BYTES + " bytes but had " + sealed.length);
        }
        return readNonce(sealed);
    }

    private static void writeNonce(long nonce, byte[] destination) {
        for (int i = 0; i < NONCE_BYTES; i++) {
            destination[i] = (byte) (nonce >>> (56 - 8 * i));
        }
    }

    private static long readNonce(byte[] source) {
        long nonce = 0;
        for (int i = 0; i < NONCE_BYTES; i++) {
            nonce = (nonce << 8) | (source[i] & 0xFF);
        }
        return nonce;
    }
}
