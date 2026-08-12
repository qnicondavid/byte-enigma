package io.github.qnicondavid.byteenigma.breaker;

/**
 * Says how much a run of bytes looks like the language you are hoping for.
 *
 * <p>Higher is better. Scores are only ever compared against other scores of the same length,
 * so the absolute number means nothing on its own.
 *
 * <p>Implementations are called once per candidate key, on every worker thread at once, so they
 * must be thread-safe and should allocate nothing.
 */
@FunctionalInterface
public interface PlaintextScorer {

    /**
     * @param plaintext a scratch buffer that may be longer than the data
     * @param length    how many bytes of it are real
     */
    double score(byte[] plaintext, int length);
}
