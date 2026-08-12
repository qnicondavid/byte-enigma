package io.github.qnicondavid.byteenigma.breaker;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SeedEvaluator;

/**
 * Ciphertext-only attack: decrypts under every key and keeps whichever result reads most like
 * English.
 *
 * <p>This is the one that does not need you to know anything about the message. It needs the
 * plaintext to be natural-language text and nothing else, which is a far weaker assumption than
 * a crib, and it is the assumption under which most real traffic was broken.
 *
 * <p>It is also much slower than {@link CribMatcher}, and unavoidably so: a crib can reject a
 * key after one byte, whereas a language score has to see the whole message before it means
 * anything. Every key costs a full decryption plus a scoring pass.
 *
 * <p>Immutable if the scorer is, which {@link QuadgramScorer} is.
 */
public final class QuadgramSearch implements SeedEvaluator<ByteEnigma> {

    private final PlaintextScorer scorer;

    public QuadgramSearch(PlaintextScorer scorer) {
        this.scorer = scorer;
    }

    /** A search backed by the quadgram table that ships in the jar. */
    public static QuadgramSearch usingBundledTable() {
        return new QuadgramSearch(QuadgramScorer.fromResource());
    }

    @Override
    public Candidate evaluate(int key, ByteEnigma machine, byte[] ciphertext, byte[] scratch) {
        machine.rekey(key);
        int length = machine.transform(ciphertext, scratch);
        return Candidate.of(key, scorer.score(scratch, length), scratch, length);
    }
}
