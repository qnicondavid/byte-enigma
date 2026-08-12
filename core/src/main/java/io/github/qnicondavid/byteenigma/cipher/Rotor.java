package io.github.qnicondavid.byteenigma.cipher;

/**
 * One rotor: a permutation of the 256 byte values, an offset that advances as the message is
 * consumed, and the offset at which it kicks the next rotor along.
 *
 * <p>The wiring is derived from the key rather than taken from a table of historical rotors,
 * so a machine can have any number of distinct rotors instead of a fixed catalogue of eight.
 *
 * <p>Stepping is a plain odometer. A rotor advances once per byte; when it lands on its own
 * turnover offset the next rotor advances too. There is no double-stepping anomaly, because
 * there is no pawl-and-ratchet mechanism to produce one.
 *
 * <p>Not thread-safe. {@link ByteEnigma} owns its rotors and never shares them.
 */
final class Rotor {

    private final byte[] forwardMap = new byte[ByteEnigma.ALPHABET_SIZE];
    private final byte[] reverseMap = new byte[ByteEnigma.ALPHABET_SIZE];
    private final Lcg48 random = new Lcg48(0);

    private int turnoverPoint;
    private int initialPosition;
    private int position;

    Rotor(int seed, int initialPosition) {
        reseed(seed, initialPosition);
    }

    /**
     * Rebuilds the wiring in place. Allocates nothing, so a sweep can rekey without
     * producing garbage.
     */
    void reseed(int seed, int initialPosition) {
        this.turnoverPoint = Math.floorMod(seed, ByteEnigma.ALPHABET_SIZE);
        this.initialPosition = Math.floorMod(initialPosition, ByteEnigma.ALPHABET_SIZE);
        this.position = this.initialPosition;

        random.setSeed(seed);
        for (int i = 0; i < ByteEnigma.ALPHABET_SIZE; i++) {
            forwardMap[i] = (byte) i;
        }
        for (int i = ByteEnigma.ALPHABET_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            byte swap = forwardMap[i];
            forwardMap[i] = forwardMap[j];
            forwardMap[j] = swap;
        }
        for (int i = 0; i < ByteEnigma.ALPHABET_SIZE; i++) {
            reverseMap[forwardMap[i] & 0xFF] = (byte) i;
        }
    }

    /** Moves the offset back to where the key put it, so the next message starts from the same state. */
    void reset() {
        position = initialPosition;
    }

    int forward(int value) {
        return forwardMap[(value + position) & 0xFF] & 0xFF;
    }

    int backward(int value) {
        return ((reverseMap[value] & 0xFF) - position) & 0xFF;
    }

    void advance() {
        position = (position + 1) & 0xFF;
    }

    boolean atTurnover() {
        return position == turnoverPoint;
    }

    int position() {
        return position;
    }

    int turnoverPoint() {
        return turnoverPoint;
    }
}
