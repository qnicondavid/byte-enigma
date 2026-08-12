package io.github.qnicondavid.byteenigma.breaker;

import java.util.Arrays;
import java.util.Comparator;

public record Candidate(int seed, double score, byte[] plaintext) implements Comparable<Candidate> {

    public static final Comparator<Candidate> BY_SCORE = Comparator.comparingDouble(Candidate::score);

    public Candidate {
        plaintext = plaintext.clone();
    }

    public static Candidate of(int seed, double score, byte[] source, int len) {
        return new Candidate(seed, score, Arrays.copyOf(source, len));
    }

    @Override
    public int compareTo(Candidate other) {
        return BY_SCORE.compare(this, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Candidate other)) {
            return false;
        }
        return seed == other.seed
                && Double.compare(score, other.score) == 0
                && Arrays.equals(plaintext, other.plaintext);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(seed);
        result = 31 * result + Double.hashCode(score);
        result = 31 * result + Arrays.hashCode(plaintext);
        return result;
    }

    @Override
    public String toString() {
        return "Candidate[seed=" + seed + ", score=" + score + ", len=" + plaintext.length + "]";
    }
}
