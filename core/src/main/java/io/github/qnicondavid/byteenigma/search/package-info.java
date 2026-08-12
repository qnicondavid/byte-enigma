/**
 * Brute-force search over a 32-bit keyspace, with no idea that ciphers exist.
 *
 * <p>{@link io.github.qnicondavid.byteenigma.search.SeedSweep} owns the range, the worker threads,
 * the leaderboard and the clock. Everything domain-specific lives in the
 * {@link io.github.qnicondavid.byteenigma.search.SeedEvaluator} you hand it: loading the key,
 * deciding how much of the message to look at, and scoring what comes out.
 *
 * <p>This is the part of the project worth reusing. Point it at a different subject and a different
 * evaluator and it will exhaust that keyspace instead. {@code SeedSweepTest} exercises the whole
 * package with an evaluator that only does arithmetic, which is what keeps the independence honest.
 */
package io.github.qnicondavid.byteenigma.search;
