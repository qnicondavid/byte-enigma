package com.enigma.breaker;

import com.enigma.EnigmaMachine;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class BreakerDemo {

    private static final int ROTOR_COUNT = 3;
    private static final long DEMO_WINDOW_SIZE = 1L << 20;
    private static final long FULL_KEYSPACE_SIZE = 1L << 32;
    private static final long FULL_KEYSPACE_START = Integer.MIN_VALUE;
    private static final long FULL_KEYSPACE_END = (long) Integer.MAX_VALUE + 1L;

    private static final String KNOWN_PLAINTEXT =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR "
            + "WITHOUT WARNING SO WE MUST DEFEND THE COAST AT ONCE";
    private static final String CRIB_TEXT = "SOUTHERN HARBOUR";

    private BreakerDemo() {
    }

    public static void main(String[] args) {
        byte[] plaintext = KNOWN_PLAINTEXT.getBytes(StandardCharsets.UTF_8);

        long windowStart = chooseWindowStart();
        long windowEnd = windowStart + DEMO_WINDOW_SIZE;
        int secretSeed = (int) ThreadLocalRandom.current().nextLong(windowStart, windowEnd);

        byte[] ciphertext = new EnigmaMachine(secretSeed, ROTOR_COUNT).transform(plaintext);

        byte[] crib = CRIB_TEXT.getBytes(StandardCharsets.UTF_8);
        int cribOffset = KNOWN_PLAINTEXT.indexOf(CRIB_TEXT);
        boolean cribAdmissibleHere = CribMatcher.offsetAdmissible(ciphertext, crib, cribOffset);

        printBanner(windowStart, windowEnd, plaintext.length, crib.length, cribOffset, cribAdmissibleHere);

        SeedSweep sweep = new SeedSweep(ROTOR_COUNT, 1);

        SeedSweep.SweepResult cribResult = sweep.sweepParallel(
                windowStart, windowEnd, ciphertext, new CribMatcher(crib, cribOffset));

        QuadgramSearch quadgramSearch = new QuadgramSearch(QuadgramScorer.fromResource());
        SeedSweep.SweepResult quadgramResult = sweep.sweepParallel(
                windowStart, windowEnd, ciphertext, quadgramSearch);

        boolean cribRecovered = reportMode(
                "MODE 1  known-plaintext crib (CribMatcher)", cribResult, secretSeed, plaintext);
        boolean quadgramRecovered = reportMode(
                "MODE 2  ciphertext-only quadgram search (QuadgramSearch)", quadgramResult, secretSeed, plaintext);

        System.out.println();
        System.out.println("secret seed (revealed only now that recovery is complete): " + secretSeed);
        System.out.println();

        printScaleNote(cribResult, quadgramResult);
        printHonestyNote();

        if (!cribRecovered || !quadgramRecovered) {
            throw new IllegalStateException("cold recovery failed: the demo did not reproduce the secret in both modes");
        }
    }

    private static long chooseWindowStart() {
        long highestLegalStart = FULL_KEYSPACE_END - DEMO_WINDOW_SIZE;
        return ThreadLocalRandom.current().nextLong(FULL_KEYSPACE_START, highestLegalStart);
    }

    private static void printBanner(long windowStart, long windowEnd, int plaintextLength,
                                    int cribLength, int cribOffset, boolean cribAdmissibleHere) {
        System.out.println("=== Enigma breaker: cold seed recovery demo ===");
        System.out.println("this build is EDUCATIONAL, not secure; the 2^32 seed space is a deliberate toy-sized design choice");
        System.out.println();
        System.out.println("known plaintext length: " + plaintextLength + " bytes of natural-language English");
        System.out.println("mode 1 crib: \"" + CRIB_TEXT + "\" (" + cribLength + " bytes) at plaintext offset " + cribOffset);
        System.out.println("mode 1 crib offset admissible against this ciphertext: " + cribAdmissibleHere);
        System.out.println();
        System.out.println("demo search window: [" + windowStart + ", " + windowEnd + ")  size " + DEMO_WINDOW_SIZE + " seeds");
        System.out.println("the secret was drawn from inside this window and is now hidden from the breaker");
        System.out.println("the window exists ONLY to finish in seconds; the same SeedSweep code sweeps the entire");
        System.out.println("2^32 space when handed [" + FULL_KEYSPACE_START + ", " + FULL_KEYSPACE_END + ")");
        System.out.println();
    }

    private static boolean reportMode(String label, SeedSweep.SweepResult result, int secretSeed, byte[] plaintext) {
        Candidate top = result.top();
        boolean seedMatches = top != null && top.seed() == secretSeed;
        boolean plaintextMatches = top != null && Arrays.equals(top.plaintext(), plaintext);

        System.out.println(label);
        if (top == null) {
            System.out.println("  no candidate recovered");
        } else {
            System.out.println("  recovered seed:      " + top.seed());
            System.out.println("  recovered plaintext: " + new String(top.plaintext(), StandardCharsets.UTF_8));
            System.out.println("  seed matches secret:      " + seedMatches);
            System.out.println("  plaintext matches source: " + plaintextMatches);
        }
        System.out.println("  seeds tried:  " + result.seedsTried());
        System.out.printf("  elapsed:      %.3f s%n", result.elapsedSeconds());
        System.out.printf("  MEASURED rate: %,.0f keys/sec (timed on this run, not projected)%n", result.keysPerSecond());
        System.out.printf("  at that measured rate a full 2^32 sweep would take %s%n",
                formatDuration(FULL_KEYSPACE_SIZE / result.keysPerSecond()));
        System.out.println();
        return seedMatches && plaintextMatches;
    }

    private static void printScaleNote(SeedSweep.SweepResult cribResult, SeedSweep.SweepResult quadgramResult) {
        double bestMeasuredRate = Math.max(cribResult.keysPerSecond(), quadgramResult.keysPerSecond());
        System.out.println("scale: the full keyspace is " + FULL_KEYSPACE_SIZE + " seeds (2^32).");
        System.out.printf("at the faster MEASURED rate above (%,.0f keys/sec) the whole space projects to %s.%n",
                bestMeasuredRate, formatDuration(FULL_KEYSPACE_SIZE / bestMeasuredRate));
        System.out.println("this projection is arithmetic from a measured rate, not a benchmarked full run.");
        System.out.println();
    }

    private static void printHonestyNote() {
        System.out.println("honesty:");
        System.out.println("  - this is a teaching artifact, NOT a secure cipher.");
        System.out.println("  - a 2^32 seed space is brute-forceable by design; a real key would be far larger.");
        System.out.println("  - keys/sec figures above are MEASURED on this machine; full-space times are projections.");
        System.out.println("  - recovering the integer seed is NOT recovering a password: EnigmaMachine.fromPassword");
        System.out.println("    seeds from String.hashCode, which is lossy, so many passwords map to one seed.");
        System.out.println("  - mode 2 (quadgram) assumes the plaintext is natural-language English.");
    }

    private static String formatDuration(double seconds) {
        if (seconds < 90.0) {
            return String.format("%.1f s", seconds);
        }
        double minutes = seconds / 60.0;
        if (minutes < 90.0) {
            return String.format("%.1f min", minutes);
        }
        double hours = minutes / 60.0;
        if (hours < 48.0) {
            return String.format("%.2f h", hours);
        }
        return String.format("%.2f days", hours / 24.0);
    }
}
