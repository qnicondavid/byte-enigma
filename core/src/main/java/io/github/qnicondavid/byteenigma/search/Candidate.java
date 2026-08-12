package io.github.qnicondavid.byteenigma.search;

import java.util.Arrays;
import java.util.Comparator;

/**
 * One key a sweep thought was worth keeping, with the plaintext it produced and the score that
 * earned it a place.
 *
 * <p>Higher scores rank first. What a score means is entirely up to the evaluator: a crib
 * matcher hands back the length of the fragment it matched, a language scorer hands back a
 * log-probability. The sweep only ever compares them against each other.
 *
 * @param key       the key that produced this
 * @param score     evaluator-defined, higher is better
 * @param plaintext the bytes the key produced, copied on the way in and out
 */
public record Candidate(int key, double score, byte[] plaintext) implements Comparable<Candidate> {

    /** Orders by score alone, ascending, so a min-heap keeps the best. */
    public static final Comparator<Candidate> BY_SCORE = Comparator.comparingDouble(Candidate::score);

    public Candidate {
        plaintext = plaintext.clone();
    }

    /** Copies the first {@code length} bytes of a reused scratch buffer into a candidate of its own. */
    public static Candidate of(int key, double score, byte[] scratch, int length) {
        return new Candidate(key, score, Arrays.copyOf(scratch, length));
    }

    @Override
    public byte[] plaintext() {
        return plaintext.clone();
    }

    @Override
    public int compareTo(Candidate other) {
        return BY_SCORE.compare(this, other);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Candidate that
                && key == that.key
                && Double.compare(score, that.score) == 0
                && Arrays.equals(plaintext, that.plaintext);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(key);
        result = 31 * result + Double.hashCode(score);
        return 31 * result + Arrays.hashCode(plaintext);
    }

    @Override
    public String toString() {
        return "Candidate[key=" + key + ", score=" + score + ", length=" + plaintext.length + "]";
    }
}
