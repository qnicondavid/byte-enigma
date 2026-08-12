package io.github.qnicondavid.byteenigma.cipher;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * A reciprocal rotor cipher over the 256 byte values.
 *
 * <p>The layout is Enigma's - plugboard, a stack of stepping rotors, a reflector, back out
 * through the rotors, plugboard again - but nothing about it is historical. The alphabet is
 * 256 symbols rather than 26, every rotor's wiring is derived from the key instead of being
 * one of eight fixed catalogue rotors, and the stepping is a plain odometer. It will not
 * reproduce a wartime message. See {@code docs/adr/0001-derived-wiring-over-historical-rotors.md}
 * for why.
 *
 * <p><strong>This is not a secure cipher and is not meant to become one.</strong> The key is a
 * 32-bit integer, which the breaker in {@code io.github.qnicondavid.byteenigma.breaker} searches
 * end to end in about an hour. There is no authentication. The whole point of the project is
 * that the cipher is weak in ways you can watch.
 *
 * <h2>Two ways to run a message</h2>
 *
 * <p>{@link #transform(byte[], byte[])} is the textbook mode: rotor offsets come from the key
 * alone, so every message under one key is enciphered by the same position-indexed permutations.
 * Two messages that share a byte at the same offset produce ciphertexts that share that byte.
 * This is the classic keystream-reuse leak and it is left in place deliberately, because
 * demonstrating it is half of what this repository is for.
 *
 * <p>{@link #transform(byte[], byte[], long)} takes a per-message nonce and derives the rotor
 * offsets from key and nonce together, which removes that leak. It does not make the cipher
 * secure: the key is still 32 bits, and a known nonce costs the attacker nothing, because the
 * nonce is not key material. {@link Envelope} wraps this up with nonce generation and framing.
 *
 * <h2>Threading</h2>
 *
 * <p><strong>Not thread-safe.</strong> Rotor offsets are mutable state and a transform walks
 * them. Give each thread its own instance; {@code SeedSweep} does exactly that.
 */
public final class ByteEnigma {

    /** Every byte value is a symbol, so the alphabet has 256 of them. */
    public static final int ALPHABET_SIZE = 256;

    /** Upper bound on rotor count, high enough to be irrelevant and low enough to reject nonsense. */
    public static final int MAX_ROTOR_COUNT = 1024;

    private final Rotor[] rotors;
    private final Involution reflector;
    private final Involution plugboard;
    private final Lcg48 positions = new Lcg48(0);

    private int key;

    /**
     * Builds a machine from a 32-bit key.
     *
     * @param key        the whole secret; 32 bits is the entire keyspace
     * @param rotorCount how many rotors to stack, from 1 to {@link #MAX_ROTOR_COUNT}
     */
    public ByteEnigma(int key, int rotorCount) {
        if (rotorCount < 1 || rotorCount > MAX_ROTOR_COUNT) {
            throw new IllegalArgumentException(
                    "rotorCount must be between 1 and " + MAX_ROTOR_COUNT + " but was " + rotorCount);
        }
        this.key = key;
        this.rotors = new Rotor[rotorCount];
        positions.setSeed(mix(key, 0));
        for (int i = 0; i < rotorCount; i++) {
            rotors[i] = new Rotor(mix(key, i + 1), positions.nextInt(ALPHABET_SIZE));
        }
        this.reflector = new Involution(mix(key, -1));
        this.plugboard = new Involution(mix(key, -2));
    }

    /**
     * Derives a key from a passphrase and builds a machine.
     *
     * <p>The derivation is FNV-1a over the UTF-8 bytes. It is a hash, not a key derivation
     * function: there is no salt, no work factor, and the result is 32 bits wide, so it cannot
     * be better than the keyspace it feeds. Recovering the key does not recover the passphrase,
     * because roughly four billion passphrases map onto four billion keys with collisions
     * everywhere above that.
     */
    public static ByteEnigma fromPassword(String password, int rotorCount) {
        return new ByteEnigma(seedFromPassword(password), rotorCount);
    }

    /**
     * Rebuilds every component in place for a new key, allocating nothing.
     *
     * <p>Byte for byte identical to constructing a fresh machine with the same arguments, which
     * {@code RekeyEquivalenceTest} pins across the edges of the keyspace. A brute-force sweep
     * calls this once per candidate, so it is the hottest method in the project.
     */
    public void rekey(int key) {
        this.key = key;
        positions.setSeed(mix(key, 0));
        for (int i = 0; i < rotors.length; i++) {
            rotors[i].reseed(mix(key, i + 1), positions.nextInt(ALPHABET_SIZE));
        }
        reflector.reseed(mix(key, -1));
        plugboard.reseed(mix(key, -2));
    }

    /** Base64 of the transform applied to the UTF-8 bytes of {@code text}. */
    public String encrypt(String text) {
        return Base64.getEncoder().encodeToString(transform(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** The inverse of {@link #encrypt}. */
    public String decrypt(String base64) {
        return new String(transform(Base64.getDecoder().decode(base64)), StandardCharsets.UTF_8);
    }

    /** The rotors, in order. */
    public List<Rotor> rotors() {
        return Collections.unmodifiableList(Arrays.asList(rotors));
    }

    /** How many rotors this machine stacks. */
    public int rotorCount() {
        return rotors.length;
    }

    /** The key currently loaded. */
    public int key() {
        return key;
    }

    /**
     * Textbook mode: transforms the whole input, returning a fresh array.
     *
     * <p>Self-inverse. Feeding the output back in with the same key returns the input.
     */
    public byte[] transform(byte[] input) {
        byte[] output = new byte[input.length];
        transform(input, output);
        return output;
    }

    /**
     * Textbook mode into a caller-supplied buffer, so a sweep can reuse one array.
     *
     * <p>Rotor offsets come from the key alone. Read the class documentation before using this
     * for anything you would not publish.
     *
     * @return the number of bytes written, always {@code input.length}
     */
    public int transform(byte[] input, byte[] output) {
        requireCapacity(input.length, output.length);
        resetPositions();
        return run(input, output, 0, input.length);
    }

    /** Nonced mode, returning a fresh array. Never reuse a (key, nonce) pair. */
    public byte[] transform(byte[] input, long nonce) {
        byte[] output = new byte[input.length];
        transform(input, output, nonce);
        return output;
    }

    /**
     * Nonced mode into a caller-supplied buffer.
     *
     * <p>Rotor offsets come from key and nonce together, so two messages under one key no longer
     * share their permutation sequence. This closes the keystream-reuse leak and nothing else:
     * the key is still 32 bits and the message is still unauthenticated.
     *
     * @return the number of bytes written, always {@code input.length}
     */
    public int transform(byte[] input, byte[] output, long nonce) {
        requireCapacity(input.length, output.length);
        seedPositions(nonce);
        return run(input, output, 0, input.length);
    }

    /**
     * Textbook mode over one window of the message.
     *
     * <p>Steps the rotors through {@code input[0, from)} without doing the substitution work,
     * then transforms {@code input[from, to)} into the same range of {@code output}. Bytes
     * outside the window are left as they were.
     *
     * <p>This exists for the crib attack, which only ever needs to look at the handful of bytes
     * a known fragment covers. Skipping the rest is the difference between decrypting 117 bytes
     * per candidate key and decrypting 16.
     *
     * @return the number of bytes written
     */
    public int transformWindow(byte[] input, byte[] output, int from, int to) {
        requireWindow(input.length, output.length, from, to);
        resetPositions();
        return run(input, output, from, to);
    }

    /** Nonced mode over one window. See {@link #transformWindow(byte[], byte[], int, int)}. */
    public int transformWindow(byte[] input, byte[] output, int from, int to, long nonce) {
        requireWindow(input.length, output.length, from, to);
        seedPositions(nonce);
        return run(input, output, from, to);
    }

    private int run(byte[] input, byte[] output, int from, int to) {
        for (int i = 0; i < from; i++) {
            step();
        }
        for (int i = from; i < to; i++) {
            step();
            int c = plugboard.apply(input[i] & 0xFF);
            for (Rotor rotor : rotors) {
                c = rotor.forward(c);
            }
            c = reflector.apply(c);
            for (int j = rotors.length - 1; j >= 0; j--) {
                c = rotors[j].backward(c);
            }
            output[i] = (byte) plugboard.apply(c);
        }
        return to - from;
    }

    private void step() {
        for (Rotor rotor : rotors) {
            rotor.advance();
            if (!rotor.atTurnover()) {
                break;
            }
        }
    }

    private void resetPositions() {
        for (Rotor rotor : rotors) {
            rotor.reset();
        }
    }

    private void seedPositions(long nonce) {
        positions.setSeed(mix64(key, nonce));
        for (Rotor rotor : rotors) {
            rotor.positionAt(positions.nextInt(ALPHABET_SIZE));
        }
    }

    private static void requireCapacity(int inputLength, int outputLength) {
        if (outputLength < inputLength) {
            throw new IllegalArgumentException(
                    "output buffer holds " + outputLength + " bytes, need " + inputLength);
        }
    }

    private static void requireWindow(int inputLength, int outputLength, int from, int to) {
        if (from < 0 || to < from || to > inputLength) {
            throw new IllegalArgumentException(
                    "window [" + from + ", " + to + ") does not fit " + inputLength + " bytes of input");
        }
        requireCapacity(to, outputLength);
    }

    /**
     * Spreads one key into a distinct sub-key per component.
     *
     * <p>This is the 32-bit finaliser from MurmurHash3. Without it, neighbouring keys hand
     * neighbouring sub-keys to the rotors, and two components can collide outright at small
     * keys. {@code SubSeedDistinctnessTest} holds the line on that.
     */
    static int mix(int base, int tag) {
        int h = base ^ (tag * 0x9E3779B9);
        h ^= (h >>> 16);
        h *= 0x85EBCA6B;
        h ^= (h >>> 13);
        h *= 0xC2B2AE35;
        h ^= (h >>> 16);
        return h;
    }

    /** Folds key and nonce into the 64-bit value that seeds the starting rotor offsets. */
    static long mix64(int key, long nonce) {
        long z = nonce + 0x9E3779B97F4A7C15L + ((long) key * 0xD1B54A32D192ED03L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * FNV-1a over the UTF-8 bytes of the passphrase, finalised through {@link #mix}.
     *
     * <p>An earlier version used {@code String.hashCode}, which collides on structure rather
     * than on chance - {@code "Aa"} and {@code "BB"} land on the same value. {@code PasswordSeedTest}
     * pins that those pairs now separate.
     */
    static int seedFromPassword(String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        int h = 0x811C9DC5;
        for (byte b : bytes) {
            h ^= (b & 0xFF);
            h *= 0x01000193;
        }
        return mix(h, 0x50617373);
    }
}
