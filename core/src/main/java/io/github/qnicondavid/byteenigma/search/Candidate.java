package io.github.qnicondavid.byteenigma.search;

import java.util.Arrays;
import java.util.Comparator;

/**
 * One key a sweep thought was worth keeping, with the plaintext it produced and the score that
 * earned it a place.
 *
 * <p>Higher scores rank first. What a score means is entirely up to the evaluator: a crib matcher
 * hands back the length of the fragment it matched, a language scorer hands back a
 * log-probability. The sweep only ever compares them against each other.
 *
 * <p>This is a plain final class rather than a record on purpose. A record's compact constructor
 * would defensively copy the array a second time, on top of the copy {@link #of} already makes out
 * of the caller's scratch buffer, and a ciphertext-only sweep builds one of these for every key in
 * the range. One copy is enough.
 */
public final class Candidate implements Comparable<Candidate> {

    /**
     * Orders weakest first, so a bounded min-heap keeps the best.
     *
     * <p>Equal scores break towards the lower key. Without that, a crib sweep, where every hit
     * scores exactly the crib length, would return whichever tied candidate the threads happened
     * to reach first and would not give the same answer twice.
     */
    public static final Comparator<Candidate> WEAKEST_FIRST =
            Comparator.comparingDouble(Candidate::score)
                    .thenComparing(Comparator.comparingInt(Candidate::key).reversed());

    private final int key;
    private final double score;
    private final byte[] plaintext;

    private Candidate(int key, double score, byte[] owned) {
        this.key = key;
        this.score = score;
        this.plaintext = owned;
    }

    /** Copies the first {@code length} bytes of a reused scratch buffer into a candidate of its own. */
    public static Candidate of(int key, double score, byte[] scratch, int length) {
        return new Candidate(key, score, Arrays.copyOf(scratch, length));
    }

    /** Copies {@code plaintext}, so a later change to the caller's array cannot reach in here. */
    public static Candidate of(int key, double score, byte[] plaintext) {
        return new Candidate(key, score, plaintext.clone());
    }

    /** The key that produced this. */
    public int key() {
        return key;
    }

    /** Evaluator-defined, higher is better. */
    public double score() {
        return score;
    }

    /** The bytes the key produced. A fresh copy each call. */
    public byte[] plaintext() {
        return plaintext.clone();
    }

    /** How many bytes of plaintext this candidate carries, without copying them. */
    public int length() {
        return plaintext.length;
    }

    @Override
    public int compareTo(Candidate other) {
        return WEAKEST_FIRST.compare(this, other);
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

    /** Deliberately does not include the plaintext, which is usually the thing you are protecting. */
    @Override
    public String toString() {
        return "Candidate[key=" + key + ", score=" + score + ", length=" + plaintext.length + "]";
    }
}
