package io.github.qnicondavid.byteenigma.cli;

import io.github.qnicondavid.byteenigma.breaker.CribMatcher;
import io.github.qnicondavid.byteenigma.breaker.QuadgramSearch;
import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SeedEvaluator;
import io.github.qnicondavid.byteenigma.search.SeedSweep;
import io.github.qnicondavid.byteenigma.search.SweepResult;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The demonstration: encipher a message under a key nobody is told, then recover the key from
 * the ciphertext alone, twice, by two different routes.
 *
 * <p>The search runs over a window rather than the whole keyspace so that the demo finishes
 * while you are still looking at it. The window is the only thing that is scaled down: the same
 * {@code SeedSweep} handed the full range sweeps the full range, which is what
 * {@code docs/keyspace-sweep.md} records: 4,294,967,296 keys in 62.2 minutes on sixteen
 * threads of one machine, in one uninterrupted run.
 */
final class DemoCommand {

    private static final int ROTOR_COUNT = 3;
    private static final long WINDOW = 1L << 20;

    private static final String MESSAGE =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR "
            + "WITHOUT WARNING SO WE MUST DEFEND THE COAST AT ONCE";
    private static final String CRIB = "SOUTHERN HARBOUR";

    private static final String SECOND_MESSAGE =
            "THE ENEMY FLEET WILL SAIL AT DUSK AND ATTACK THE NORTHERN HARBOUR "
            + "WITHOUT WARNING SO WE MUST DEFEND THE RIVER AT ONCE";

    private DemoCommand() {
    }

    static int run(Arguments args, PrintStream out) {
        args.rejectUnknown("threads");
        int threads = args.intValue("threads", Runtime.getRuntime().availableProcessors());

        out.println("byte-enigma demonstration");
        out.println("=========================");
        out.println();

        keystreamReuse(out);
        boolean recovered = coldRecovery(out, threads);

        out.println("What this does and does not show");
        out.println("--------------------------------");
        out.println("  - The cipher is a teaching artifact. Do not use it for anything.");
        out.println("  - A 32-bit key is brute-forceable by design, and the search above is the proof.");
        out.println("  - Rates are measured on this machine. Any full-keyspace time printed above is");
        out.println("    arithmetic from that rate; docs/keyspace-sweep.md has a sweep of the whole");
        out.println("    4,294,967,296 that was actually run, with the log.");
        out.println("  - Recovering the key is not recovering a passphrase. fromPassword runs FNV-1a");
        out.println("    over the UTF-8 bytes and keeps 32 bits, so passphrases collide by the billion.");
        out.println("  - The quadgram route assumes the plaintext is English. The crib route assumes");
        out.println("    you already know a fragment. Neither assumption is exotic in practice.");

        return recovered ? 0 : 1;
    }

    /** Part one: what reusing a key without a nonce gives away, and what a nonce takes back. */
    private static void keystreamReuse(PrintStream out) {
        ByteEnigma machine = ByteEnigma.fromPassword("demonstration", ROTOR_COUNT);
        byte[] first = MESSAGE.getBytes(StandardCharsets.UTF_8);
        byte[] second = SECOND_MESSAGE.getBytes(StandardCharsets.UTF_8);
        int comparable = Math.min(first.length, second.length);

        int plaintextAgreements = agreements(first, second, comparable);
        int textbookAgreements = agreements(
                machine.transform(first), machine.transform(second), comparable);
        int noncedAgreements = agreements(
                machine.transform(first, 1L), machine.transform(second, 2L), comparable);

        out.println("1. Reusing a key without a nonce");
        out.println("--------------------------------");
        out.println("Two messages, one key. They share " + plaintextAgreements + " of their first "
                + comparable + " bytes in the clear.");
        out.println();
        out.println("  textbook mode, no nonce:  ciphertexts agree on " + textbookAgreements + " bytes");
        out.println("  sealed with a nonce:      ciphertexts agree on " + noncedAgreements + " bytes");
        out.println();
        out.println("Without a nonce the rotor offsets come from the key alone, so byte i of every");
        out.println("message meets the same permutation. Equal plaintext bytes at equal offsets come");
        out.println("out equal, and the ciphertext hands an eavesdropper a map of where two messages");
        out.println("agree. A nonce moves the offsets per message and that correspondence goes away.");
        out.println();
        out.println("It does not make the cipher safe. The key is still 32 bits, which is what the");
        out.println("rest of this demonstration is about.");
        out.println();
    }

    /** Part two: recovering a key nobody was told, by crib and by language. */
    private static boolean coldRecovery(PrintStream out, int threads) {
        byte[] plaintext = MESSAGE.getBytes(StandardCharsets.UTF_8);
        byte[] crib = CRIB.getBytes(StandardCharsets.UTF_8);
        int cribOffset = MESSAGE.indexOf(CRIB);

        long windowStart = ThreadLocalRandom.current()
                .nextLong(SeedSweep.KEYSPACE_START, SeedSweep.KEYSPACE_END - WINDOW);
        long windowEnd = windowStart + WINDOW;
        int secret = (int) ThreadLocalRandom.current().nextLong(windowStart, windowEnd);
        byte[] ciphertext = new ByteEnigma(secret, ROTOR_COUNT).transform(plaintext);

        out.println("2. Recovering a key nobody was told");
        out.println("-----------------------------------");
        out.println("plaintext:  " + plaintext.length + " bytes of English");
        out.println("crib:       \"" + CRIB + "\" at offset " + cribOffset);
        out.printf(Locale.ROOT, "offsets:    %.1f%% of crib positions are ruled out for free, because no byte%n",
                100.0 * CribMatcher.eliminationRate(ciphertext, crib));
        out.println("            can ever encrypt to itself");
        out.println("window:     [" + windowStart + ", " + windowEnd + ")  " + WINDOW + " keys");
        out.println("            the key was drawn from inside it and is now hidden from the search");
        out.println("threads:    " + threads);
        out.println();

        SeedSweep<ByteEnigma> sweep = new SeedSweep<>(() -> new ByteEnigma(0, ROTOR_COUNT), 1);
        boolean cribWorked = attempt(out, "crib, known plaintext", sweep, windowStart, windowEnd,
                ciphertext, new CribMatcher(crib, cribOffset), threads, secret, plaintext);
        boolean languageWorked = attempt(out, "quadgrams, ciphertext only", sweep, windowStart, windowEnd,
                ciphertext, QuadgramSearch.usingBundledTable(), threads, secret, plaintext);

        out.println("The key was " + secret + ", revealed only now that both routes have found it.");
        out.println();
        return cribWorked && languageWorked;
    }

    private static boolean attempt(PrintStream out, String label, SeedSweep<ByteEnigma> sweep,
                                   long from, long to, byte[] ciphertext,
                                   SeedEvaluator<ByteEnigma> evaluator, int threads,
                                   int secret, byte[] expected) {
        SweepResult result = sweep.sweepParallel(from, to, ciphertext, evaluator, threads);
        Candidate top = result.top();
        boolean keyMatches = top != null && top.key() == secret;
        boolean textMatches = top != null && Arrays.equals(top.plaintext(), expected);

        out.println("  " + label);
        if (top == null) {
            out.println("    nothing recovered");
        } else {
            out.println("    key:        " + top.key() + (keyMatches ? "  (correct)" : "  (WRONG)"));
            out.println("    plaintext:  " + new String(top.plaintext(), StandardCharsets.UTF_8));
            out.println("    matches:    " + textMatches);
        }
        out.printf(Locale.ROOT, "    elapsed:    %s for %,d keys%n",
                Durations.format(result.elapsedSeconds()), result.keysTried());
        out.printf(Locale.ROOT, "    rate:       %,.0f keys/sec, so 2^32 projects to %s%n",
                result.keysPerSecond(), Durations.format(result.fullKeyspaceSeconds()));
        out.println();
        return keyMatches && textMatches;
    }

    private static int agreements(byte[] left, byte[] right, int length) {
        int count = 0;
        for (int i = 0; i < length; i++) {
            if (left[i] == right[i]) {
                count++;
            }
        }
        return count;
    }
}
