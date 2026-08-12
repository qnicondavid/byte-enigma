package io.github.qnicondavid.byteenigma.search;

/**
 * Decides what one candidate key is worth.
 *
 * <p>The sweep owns the loop, the threads and the leaderboard. Everything that knows anything
 * about the cipher lives here: loading the key into the subject, deciding how much of the
 * message to look at, and scoring what comes out.
 *
 * <p>Implementations run on many threads at once, each with its own {@code subject} and its own
 * {@code scratch}, so they may hold no mutable state of their own.
 *
 * @param <T> whatever the evaluator rekeys and applies - a cipher instance, usually
 */
@FunctionalInterface
public interface SeedEvaluator<T> {

    /**
     * Scores one key, or rejects it.
     *
     * @param key        the key to try
     * @param subject    this thread's private instance, ready to be rekeyed
     * @param ciphertext the message under attack, shared and read-only
     * @param scratch    this thread's private output buffer, at least as long as the ciphertext
     * @return a candidate, or {@code null} to discard the key without ranking it
     */
    Candidate evaluate(int key, T subject, byte[] ciphertext, byte[] scratch);
}
