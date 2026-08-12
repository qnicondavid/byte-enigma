package io.github.qnicondavid.byteenigma.cipher;

/**
 * A fixed-point-free permutation of the 256 byte values that is its own inverse.
 *
 * <p>The 256 values are shuffled and then paired off, giving 128 transpositions and no value
 * left mapping to itself. Applying it twice is the identity, which is what makes the machine
 * as a whole reciprocal: the same setup both encrypts and decrypts.
 *
 * <p>Both the reflector and the plugboard are instances of this. That is a deliberate departure
 * from the historical Enigma, where the plugboard swapped only about ten letter pairs and left
 * the rest untouched - see {@code docs/adr/0001-derived-wiring-over-historical-rotors.md}.
 *
 * <p>The absence of fixed points is the flaw that makes the crib attack in
 * {@code io.github.qnicondavid.byteenigma.breaker} cheap: no byte can ever encrypt to itself,
 * so a candidate crib position can be ruled out before a single key is tried.
 */
final class Involution {

    private final byte[] map = new byte[ByteEnigma.ALPHABET_SIZE];
    private final byte[] pool = new byte[ByteEnigma.ALPHABET_SIZE];
    private final Lcg48 random = new Lcg48(0);

    Involution(int seed) {
        reseed(seed);
    }

    /**
     * Rebuilds the wiring in place from {@code seed}. Allocates nothing: the shuffle pool and
     * the generator are retained across calls so that a sweep can rekey millions of times
     * without producing garbage.
     */
    void reseed(int seed) {
        for (int i = 0; i < ByteEnigma.ALPHABET_SIZE; i++) {
            pool[i] = (byte) i;
        }
        random.setSeed(seed);
        for (int i = ByteEnigma.ALPHABET_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            byte swap = pool[i];
            pool[i] = pool[j];
            pool[j] = swap;
        }
        for (int i = 0; i < ByteEnigma.ALPHABET_SIZE; i += 2) {
            int a = pool[i] & 0xFF;
            int b = pool[i + 1] & 0xFF;
            map[a] = (byte) b;
            map[b] = (byte) a;
        }
    }

    /** Maps one byte value to its partner. Never returns its argument. */
    int apply(int value) {
        return map[value] & 0xFF;
    }
}
