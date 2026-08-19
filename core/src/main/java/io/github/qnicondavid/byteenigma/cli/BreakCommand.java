package io.github.qnicondavid.byteenigma.cli;

import io.github.qnicondavid.byteenigma.breaker.CribMatcher;
import io.github.qnicondavid.byteenigma.breaker.QuadgramSearch;
import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.ScoreHistogram;
import io.github.qnicondavid.byteenigma.search.SeedEvaluator;
import io.github.qnicondavid.byteenigma.search.SeedSweep;
import io.github.qnicondavid.byteenigma.search.SweepCheckpoint;
import io.github.qnicondavid.byteenigma.search.SweepResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** The {@code break} and {@code offsets} subcommands. */
final class BreakCommand {

    private BreakCommand() {
    }

    private static final String[] BREAK_OPTIONS = {
        "crib", "at", "language", "rotors", "in", "binary", "from", "to",
        "threads", "top", "progress", "checkpoint", "segment", "for", "histogram"
    };

    private static final String[] OFFSET_OPTIONS = {"crib", "in", "binary"};

    /** Default keys per segment: about four minutes of work on two cores. */
    private static final long DEFAULT_SEGMENT = 1L << 26;

    // Where the scores of a whole sweep are expected to land, for --histogram. A sweep cannot make
    // two passes over four billion keys to find its own range, so this is a guess: the best key of
    // the published sweep scored -1620 and the tenth scored -1830, and the bulk sits below that.
    // The file reports how many fell outside, which is how you find out the guess was wrong.
    private static final double HISTOGRAM_LO = -2200.0;
    private static final double HISTOGRAM_HI = -1500.0;
    private static final double HISTOGRAM_BIN = 0.1;

    static int run(Arguments args, InputStream stdin, PrintStream stdout) throws IOException {
        args.rejectUnknown(BREAK_OPTIONS);
        byte[] ciphertext = readCiphertext(args, stdin);
        int rotors = args.intValue("rotors", 3);
        int topN = args.intValue("top", 1);
        int threads = args.intValue("threads", Runtime.getRuntime().availableProcessors());
        long from = args.longValue("from", SeedSweep.KEYSPACE_START);
        long to = args.longValue("to", SeedSweep.KEYSPACE_END);
        long segment = Math.max(1L, args.longValue("segment", DEFAULT_SEGMENT));
        long budgetSeconds = args.longValue("for", 0L);

        // Validated here rather than left to the sweep, because a segmented run would otherwise
        // just find an empty loop and report "no candidate survived" for what is a typo.
        if (to < from) {
            throw new Arguments.UsageException(
                    "--to must be at least --from, got [" + from + ", " + to + ")");
        }
        if (to - from > (1L << 32)) {
            throw new Arguments.UsageException("range is wider than the 2^32 keyspace");
        }
        // Without this the endpoints are cast to int inside the sweep and wrap, so a run over
        // [4294967296, 4294967396) reports a hundred keys tried and names a winner it never saw.
        if (from < SeedSweep.KEYSPACE_START || to > SeedSweep.KEYSPACE_END) {
            throw new Arguments.UsageException("--from and --to must lie inside ["
                    + SeedSweep.KEYSPACE_START + ", " + SeedSweep.KEYSPACE_END + ")");
        }
        if (threads < 1) {
            throw new Arguments.UsageException("--threads must be at least 1 but was " + threads);
        }
        if (topN < 1) {
            throw new Arguments.UsageException("--top must be at least 1 but was " + topN);
        }

        boolean language = args.flag("language");
        boolean crib = args.has("crib");
        if (language == crib) {
            throw new Arguments.UsageException("choose exactly one of --crib <text> or --language");
        }

        SeedEvaluator<ByteEnigma> evaluator;
        String mode;
        String label;
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
            mode = "crib:" + offset + ":" + fragment.length;
            label = "crib \"" + args.require("crib") + "\" at offset " + offset;
            warnIfCribIsShort(stdout, fragment.length, to - from, topN);
        } else {
            evaluator = QuadgramSearch.usingBundledTable();
            mode = "language";
            label = "ciphertext-only quadgram search";
        }

        Path checkpointPath = args.has("checkpoint") ? Path.of(args.require("checkpoint")) : null;
        String digest = SweepCheckpoint.digestOf(ciphertext);
        SweepCheckpoint resumed = checkpointPath == null ? null : SweepCheckpoint.load(checkpointPath);
        if (resumed != null) {
            resumed.requireMatches(mode, digest, from, to);
        }

        Path histogramPath = args.has("histogram") ? Path.of(args.require("histogram")) : null;
        if (histogramPath != null) {
            if (crib) {
                throw new Arguments.UsageException("--histogram needs --language. A crib evaluator "
                        + "rejects almost every key without scoring it, so the file would hold one entry.");
            }
            if (resumed != null) {
                throw new Arguments.UsageException("--histogram counts only the keys this run tries, "
                        + "and this one resumes at " + resumed.cursor() + ". Start from the beginning "
                        + "or drop the flag.");
            }
        }

        long cursor = resumed == null ? from : resumed.cursor();
        long keysTried = resumed == null ? 0L : resumed.keysTried();
        long elapsedNanos = resumed == null ? 0L : resumed.elapsedNanos();
        List<Candidate> best = resumed == null ? new ArrayList<>() : new ArrayList<>(resumed.best());

        long total = to - from;
        stdout.println("ciphertext: " + ciphertext.length + " bytes, sha-256 " + digest.substring(0, 16));
        stdout.println("mode:       " + label);
        stdout.println("range:      [" + from + ", " + to + ")  " + total + " keys");
        stdout.println("threads:    " + threads);
        if (checkpointPath != null) {
            stdout.println("checkpoint: " + checkpointPath
                    + (resumed == null ? "  (new)" : "  (resuming at " + cursor + ")"));
            stdout.printf(Locale.ROOT, "resumed:    %,d keys already done, %.1f%% of the range%n",
                    keysTried, 100.0 * (resumed == null ? 0.0 : resumed.fraction()));
        }
        if (budgetSeconds > 0L) {
            stdout.println("budget:     " + Durations.format(budgetSeconds)
                    + (checkpointPath == null
                            ? ", then stop. No --checkpoint, so the work is not saved."
                            : ", then stop and checkpoint"));
        }
        stdout.println();

        SeedSweep<ByteEnigma> sweep = new SeedSweep<>(() -> new ByteEnigma(0, rotors), topN);
        long progressSeconds = args.longValue("progress", segment > (1L << 24) ? 60L : 0L);
        if (progressSeconds > 0L && checkpointPath == null) {
            sweep = sweep.reportingTo(new SweepProgressPrinter(stdout), progressSeconds * 1000L);
        }
        ScoreHistogram histogram = null;
        if (histogramPath != null) {
            histogram = new ScoreHistogram(HISTOGRAM_LO, HISTOGRAM_HI, HISTOGRAM_BIN);
            sweep = sweep.recordingScoresInto(histogram);
        }

        long budgetNanos = budgetSeconds * 1_000_000_000L;
        long spentThisRun = 0L;
        boolean stoppedEarly = false;

        while (cursor < to) {
            long segmentEnd = Math.min(cursor + segment, to);
            SweepResult result = sweep.sweepParallel(cursor, segmentEnd, ciphertext, evaluator, threads);

            best.addAll(result.best());
            best.sort(Candidate.WEAKEST_FIRST.reversed());
            if (best.size() > topN) {
                best = new ArrayList<>(best.subList(0, topN));
            }
            cursor = segmentEnd;
            keysTried += result.keysTried();
            elapsedNanos += result.elapsedNanos();
            spentThisRun += result.elapsedNanos();

            if (checkpointPath != null) {
                new SweepCheckpoint(mode, digest, from, to, cursor, keysTried, elapsedNanos, best)
                        .save(checkpointPath);
                stdout.printf(Locale.ROOT, "  %6.2f%%  %,15d keys  %,8.0f keys/sec  this run %s  total %s%n",
                        100.0 * (cursor - from) / total, keysTried,
                        keysTried / (elapsedNanos / 1_000_000_000.0),
                        Durations.format(spentThisRun / 1_000_000_000.0),
                        Durations.format(elapsedNanos / 1_000_000_000.0));
            }

            if (budgetNanos > 0L && spentThisRun >= budgetNanos && cursor < to) {
                stoppedEarly = true;
                break;
            }
        }

        stdout.println();
        if (stoppedEarly) {
            stdout.printf(Locale.ROOT, "stopped on budget at %,d of %,d keys (%.2f%%). Run the same command again%n",
                    cursor - from, total, 100.0 * (cursor - from) / total);
            stdout.println(checkpointPath == null
                    ? "from the beginning: nothing was saved, because there was no --checkpoint."
                    : "to carry on from here.");
            stdout.println();
        }
        if (histogram != null) {
            Files.writeString(histogramPath, histogram.render(), StandardCharsets.UTF_8);
            stdout.printf(Locale.ROOT, "histogram:  %,d scores written to %s%n", histogram.counted(), histogramPath);
            if (histogram.counted() > 0L) {
                stdout.printf(Locale.ROOT, "            lowest %.2f, highest %.2f%n",
                        histogram.lowest(), histogram.highest());
            }
            if (histogram.under() > 0L || histogram.over() > 0L) {
                stdout.printf(Locale.ROOT, "            %,d fell below %.0f and %,d above %.0f, so the bins were "
                        + "chosen badly%n", histogram.under(), HISTOGRAM_LO, histogram.over(), HISTOGRAM_HI);
            }
            if (stoppedEarly) {
                stdout.println("            this run stopped early, so the file is not the whole range");
            }
            stdout.println();
        }
        report(stdout, keysTried, elapsedNanos, best, topN, cursor >= to);
        if (stoppedEarly) {
            return 3;
        }
        return best.isEmpty() ? 1 : 0;
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
        stdout.printf(Locale.ROOT, "eliminated:  %.1f%% before a single key was tried%n",
                100.0 * CribMatcher.eliminationRate(ciphertext, crib));
        stdout.println();
        stdout.println("offsets: " + admissible);
        return 0;
    }

    /**
     * A wrong key matches an L-byte crib with probability 256^-L, so a short crib over a wide range
     * will produce hits that are not the key. Every crib hit scores exactly the crib length, so
     * those hits tie with the real one and the leaderboard has no way to prefer it. Saying so
     * beforehand is cheaper than letting someone act on a confident wrong answer.
     */
    private static void warnIfCribIsShort(PrintStream stdout, int cribLength, long range, int topN) {
        double expectedFalseHits = range * Math.pow(2.0, -8.0 * cribLength);
        if (expectedFalseHits <= 0.01) {
            return;
        }
        stdout.printf(Locale.ROOT, "warning: a %d-byte crib is expected to match about %.3g wrong keys over this%n",
                cribLength, expectedFalseHits);
        stdout.println("         range by chance. Every crib hit scores the same, so a wrong one can");
        stdout.println("         outrank the key. Use a longer crib, or --top " + Math.max(topN, 10)
                + " and check the results yourself.");
        stdout.println();
    }

    private static void report(PrintStream stdout, long keysTried, long elapsedNanos,
                              List<Candidate> best, int topN, boolean complete) {
        double seconds = elapsedNanos / 1_000_000_000.0;
        stdout.println("keys tried:  " + String.format(Locale.ROOT, "%,d", keysTried)
                + (complete ? "  (whole range)" : ""));
        stdout.printf(Locale.ROOT, "elapsed:     %s%n", Durations.format(seconds));
        stdout.printf(Locale.ROOT, "rate:        %,.0f keys/sec (measured over the work actually done)%n",
                seconds > 0.0 ? keysTried / seconds : 0.0);
        if (!complete) {
            stdout.printf(Locale.ROOT, "full 2^32:   %s at that rate%n",
                    Durations.format(seconds > 0.0 ? (1L << 32) / (keysTried / seconds) : Double.NaN));
        }
        stdout.println();

        if (best.isEmpty()) {
            stdout.println("no candidate survived");
            return;
        }
        long tiedAtTop = best.stream().filter(c -> c.score() == best.get(0).score()).count();
        if (tiedAtTop > 1) {
            stdout.println(tiedAtTop + " candidates tie for the top score, so the ordering between");
            stdout.println("them is arbitrary. None of them is more likely than the others.");
            stdout.println();
        }
        if (best.size() > 1) {
            stdout.printf(Locale.ROOT, "margin:      %.2f between the first and the second%n%n",
                    best.get(0).score() - best.get(1).score());
        }

        int shown = Math.min(topN, best.size());
        for (int i = 0; i < shown; i++) {
            Candidate candidate = best.get(i);
            stdout.printf(Locale.ROOT, "#%d  key=%d  score=%.2f%n", i + 1, candidate.key(), candidate.score());
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
        // Line breaks come out, and the padding around them, because docs/keyspace-sweep.md
        // prints its ciphertext wrapped and a mail client will wrap it again. A space inside a
        // line stays, so a file of prose is still refused: strip every space instead and
        // "attack at dawn" becomes twelve characters of the Base64 alphabet and decodes.
        StringBuilder joined = new StringBuilder();
        new String(raw, StandardCharsets.UTF_8).lines().map(String::strip).forEach(joined::append);
        String text = joined.toString();
        try {
            return Base64.getDecoder().decode(text);
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
            out.printf(Locale.ROOT, "  %5.1f%%  %,d keys  %,.0f keys/sec  elapsed %s  left %s%n",
                    100.0 * fraction, keysTried, keysPerSecond,
                    Durations.format(elapsedSeconds), Durations.format(remaining));
        }
    }
}
