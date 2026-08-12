package io.github.qnicondavid.byteenigma.cli;

import io.github.qnicondavid.byteenigma.breaker.CribMatcher;
import io.github.qnicondavid.byteenigma.breaker.QuadgramSearch;
import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SeedEvaluator;
import io.github.qnicondavid.byteenigma.search.SeedSweep;
import io.github.qnicondavid.byteenigma.search.SweepResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/** The {@code break} and {@code offsets} subcommands. */
final class BreakCommand {

    private BreakCommand() {
    }

    private static final String[] BREAK_OPTIONS = {
        "crib", "at", "language", "rotors", "in", "binary", "from", "to", "threads", "top", "progress"
    };

    private static final String[] OFFSET_OPTIONS = {"crib", "in", "binary"};

    static int run(Arguments args, InputStream stdin, PrintStream stdout) throws IOException {
        args.rejectUnknown(BREAK_OPTIONS);
        byte[] ciphertext = readCiphertext(args, stdin);
        int rotors = args.intValue("rotors", 3);
        int topN = args.intValue("top", 1);
        int threads = args.intValue("threads", Runtime.getRuntime().availableProcessors());
        long from = args.longValue("from", SeedSweep.KEYSPACE_START);
        long to = args.longValue("to", SeedSweep.KEYSPACE_END);

        boolean language = args.flag("language");
        boolean crib = args.has("crib");
        if (language == crib) {
            throw new Arguments.UsageException(
                    "choose exactly one of --crib <text> or --language");
        }

        SeedEvaluator<ByteEnigma> evaluator;
        String mode;
        if (crib) {
            byte[] fragment = args.require("crib").getBytes(StandardCharsets.UTF_8);
            int offset = args.intValue("at", -1);
            if (offset < 0) {
                throw new Arguments.UsageException(
                        "--crib needs --at <offset>; run the offsets command to see which offsets are possible");
            }
            if (!CribMatcher.offsetAdmissible(ciphertext, fragment, offset)) {
                stdout.println("offset " + offset + " is impossible for this crib: the crib agrees with the");
                stdout.println("ciphertext somewhere inside it, and no byte ever encrypts to itself.");
                return 2;
            }
            evaluator = new CribMatcher(fragment, offset);
            mode = "crib \"" + args.require("crib") + "\" at offset " + offset;
        } else {
            evaluator = QuadgramSearch.usingBundledTable();
            mode = "ciphertext-only quadgram search";
        }

        long total = to - from;
        stdout.println("ciphertext: " + ciphertext.length + " bytes");
        stdout.println("mode:       " + mode);
        stdout.println("range:      [" + from + ", " + to + ")  " + total + " keys");
        stdout.println("threads:    " + threads);
        stdout.println();

        SeedSweep<ByteEnigma> sweep = new SeedSweep<>(() -> new ByteEnigma(0, rotors), topN);
        long progressSeconds = args.longValue("progress", total > (1L << 26) ? 30L : 0L);
        if (progressSeconds > 0L) {
            sweep = sweep.reportingTo(progressReporter(stdout), progressSeconds * 1000L);
        }

        SweepResult result = sweep.sweepParallel(from, to, ciphertext, evaluator, threads);
        report(stdout, result, topN);
        return result.top() == null ? 1 : 0;
    }

    /** Reports which crib positions survive the no-fixed-point rule, without trying any key. */
    static int offsets(Arguments args, InputStream stdin, PrintStream stdout) throws IOException {
        args.rejectUnknown(OFFSET_OPTIONS);
        byte[] ciphertext = readCiphertext(args, stdin);
        byte[] crib = args.require("crib").getBytes(StandardCharsets.UTF_8);
        if (crib.length > ciphertext.length) {
            stdout.println("crib is longer than the ciphertext");
            return 2;
        }
        List<Integer> admissible = CribMatcher.admissibleOffsets(ciphertext, crib);
        int positions = ciphertext.length - crib.length + 1;
        stdout.println("ciphertext:  " + ciphertext.length + " bytes");
        stdout.println("crib:        " + crib.length + " bytes");
        stdout.println("positions:   " + positions);
        stdout.println("admissible:  " + admissible.size());
        stdout.printf("eliminated:  %.1f%% before a single key was tried%n",
                100.0 * CribMatcher.eliminationRate(ciphertext, crib));
        stdout.println();
        stdout.println("offsets: " + admissible);
        return 0;
    }

    private static SweepProgressPrinter progressReporter(PrintStream stdout) {
        return new SweepProgressPrinter(stdout);
    }

    private static void report(PrintStream stdout, SweepResult result, int topN) {
        stdout.println();
        stdout.println("keys tried:  " + result.keysTried());
        stdout.printf("elapsed:     %s%n", Durations.format(result.elapsedSeconds()));
        stdout.printf("rate:        %,.0f keys/sec (measured on this run)%n", result.keysPerSecond());
        stdout.printf("full 2^32:   %s at that rate%n", Durations.format(result.fullKeyspaceSeconds()));
        stdout.println();

        List<Candidate> best = result.best();
        if (best.isEmpty()) {
            stdout.println("no candidate survived");
            return;
        }
        int shown = Math.min(topN, best.size());
        for (int i = 0; i < shown; i++) {
            Candidate candidate = best.get(i);
            stdout.printf("#%d  key=%d  score=%.2f%n", i + 1, candidate.key(), candidate.score());
            stdout.println("    " + preview(candidate.plaintext()));
        }
    }

    private static String preview(byte[] plaintext) {
        String text = new String(plaintext, StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}]", ".");
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    private static byte[] readCiphertext(Arguments args, InputStream stdin) throws IOException {
        byte[] raw = args.has("in")
                ? Files.readAllBytes(Path.of(args.require("in")))
                : stdin.readAllBytes();
        if (args.flag("binary")) {
            return raw;
        }
        try {
            return Base64.getDecoder().decode(new String(raw, StandardCharsets.UTF_8).trim());
        } catch (IllegalArgumentException notBase64) {
            throw new Arguments.UsageException("ciphertext is not Base64; pass --binary if it is raw bytes");
        }
    }

    /** Prints a progress line, so an hour-long sweep is not a silent hour. */
    private record SweepProgressPrinter(PrintStream out)
            implements io.github.qnicondavid.byteenigma.search.SweepProgress {

        @Override
        public void report(long keysTried, long keysTotal, double keysPerSecond, double elapsedSeconds) {
            double fraction = keysTotal > 0 ? (double) keysTried / keysTotal : 0.0;
            double remaining = keysPerSecond > 0.0 ? (keysTotal - keysTried) / keysPerSecond : Double.NaN;
            out.printf("  %5.1f%%  %,d keys  %,.0f keys/sec  elapsed %s  left %s%n",
                    100.0 * fraction, keysTried, keysPerSecond,
                    Durations.format(elapsedSeconds), Durations.format(remaining));
        }
    }
}
