package io.github.qnicondavid.byteenigma.breaker;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SeedEvaluator;
import java.util.ArrayList;
import java.util.List;

/**
 * Known-plaintext attack: accepts a key only if it turns the ciphertext into a fragment you
 * already know, at a position you already know.
 *
 * <p>Bletchley Park called such a fragment a crib, and found them in weather reports and in the
 * habit of ending messages the same way. Here it is whatever you are willing to bet on -
 * a header, a name, a filename.
 *
 * <h2>Only the crib is ever decrypted</h2>
 *
 * <p>A wrong key fails on the first byte with probability 255/256, so decrypting the whole
 * message before checking is almost all wasted work. This evaluator uses
 * {@link ByteEnigma#transformWindow} to step the rotors through the prefix without doing the
 * substitution work and decrypt only the bytes the crib covers. The full plaintext is recovered
 * afterwards, on the one key in four billion that survives.
 *
 * <h2>Ruling out positions before trying a single key</h2>
 *
 * <p>The cipher can never map a byte to itself. So if the crib and the ciphertext agree on any
 * byte of a candidate window, the crib cannot sit there, and no key needs to be tried to know it.
 * {@link #admissibleOffsets} does that arithmetic. On English text it usually throws away most
 * of the positions for free - the same reciprocity that makes the machine self-inverse is what
 * hands the attacker that discount.
 *
 * <p>Immutable and safe to share across worker threads.
 */
public final class CribMatcher implements SeedEvaluator<ByteEnigma> {

    private final byte[] crib;
    private final int offset;

    /**
     * @param crib   the plaintext fragment you expect to find, at least one byte
     * @param offset where in the message you expect it
     */
    public CribMatcher(byte[] crib, int offset) {
        if (crib.length == 0) {
            throw new IllegalArgumentException("crib must not be empty");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative but was " + offset);
        }
        this.crib = crib.clone();
        this.offset = offset;
    }

    @Override
    public Candidate evaluate(int key, ByteEnigma machine, byte[] ciphertext, byte[] scratch) {
        int end = offset + crib.length;
        if (end > ciphertext.length) {
            return null;
        }
        machine.rekey(key);
        machine.transformWindow(ciphertext, scratch, offset, end);
        for (int i = 0; i < crib.length; i++) {
            if (scratch[offset + i] != crib[i]) {
                return null;
            }
        }
        machine.transform(ciphertext, scratch);
        return Candidate.of(key, crib.length, scratch, ciphertext.length);
    }

    /**
     * Whether the crib could sit at {@code offset} at all, judged without trying any key.
     *
     * <p>False as soon as the crib agrees with the ciphertext on one byte, because a byte never
     * encrypts to itself.
     */
    public static boolean offsetAdmissible(byte[] ciphertext, byte[] crib, int offset) {
        if (offset < 0 || crib.length == 0 || offset + crib.length > ciphertext.length) {
            return false;
        }
        for (int i = 0; i < crib.length; i++) {
            if (crib[i] == ciphertext[offset + i]) {
                return false;
            }
        }
        return true;
    }

    /** Every position the crib could still occupy, in ascending order. */
    public static List<Integer> admissibleOffsets(byte[] ciphertext, byte[] crib) {
        List<Integer> offsets = new ArrayList<>();
        for (int offset = 0; offset + crib.length <= ciphertext.length; offset++) {
            if (offsetAdmissible(ciphertext, crib, offset)) {
                offsets.add(offset);
            }
        }
        return offsets;
    }

    /** How many positions the no-fixed-point rule eliminates, as a fraction of all of them. */
    public static double eliminationRate(byte[] ciphertext, byte[] crib) {
        int positions = ciphertext.length - crib.length + 1;
        if (positions <= 0) {
            return 0.0;
        }
        return 1.0 - (double) admissibleOffsets(ciphertext, crib).size() / positions;
    }
}
