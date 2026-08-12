package io.github.qnicondavid.byteenigma.breaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Scores a run of bytes by how often its four-letter sequences turn up in English.
 *
 * <p>This is what makes the ciphertext-only attack possible. A crib attack needs you to already
 * know a fragment of the plaintext; this one only needs you to know the plaintext is English.
 * Every candidate key produces 26^4 possible quadgrams' worth of nonsense, and the true key
 * produces text, and text scores several hundred log-units higher.
 *
 * <h2>Every candidate is scored over the same number of windows</h2>
 *
 * <p>The score is a sum of log-probabilities over the {@code length - 3} sliding windows of the
 * decrypted bytes, and every window contributes exactly one term: a real probability if all four
 * bytes are letters, the unseen-quadgram floor otherwise. Because each term is negative, any
 * scoring rule that lets one candidate contribute fewer terms than another hands that candidate
 * a better score for free.
 *
 * <p>An earlier version of this class went through a {@code String}, decoding UTF-8 where it
 * could and falling back to Latin-1 where it could not. That is exactly such a rule: a candidate
 * whose bytes happened to form valid multi-byte UTF-8 decoded to fewer characters, so it was
 * scored over fewer windows and floated to the top on nothing. Bytes are now scored as bytes
 * through a 256-entry lookup table, which removes the bias, removes an allocation per candidate,
 * and is a good deal faster.
 *
 * <p>Instances are immutable and safe to share across threads.
 */
public final class QuadgramScorer implements PlaintextScorer {

    /** Where the shipped table lives on the classpath. */
    public static final String RESOURCE = "/io/github/qnicondavid/byteenigma/breaker/quadgrams.txt";

    static final int ALPHABET_SIZE = 26;
    static final int QUADGRAM_LENGTH = 4;
    static final int QUADGRAM_SPACE = ALPHABET_SIZE * ALPHABET_SIZE * ALPHABET_SIZE * ALPHABET_SIZE;

    /**
     * Maps a byte to a letter index, or -1. Upper and lower case fold together; everything else,
     * including spaces and punctuation, is not a letter and drags its windows down to the floor.
     */
    private static final int[] LETTER_INDEX = buildLetterIndex();

    private static final double UNSEEN_MASS_FRACTION = 0.01;
    private static final String TOTAL_HEADER_KEY = "total=";

    private final double[] logProbability;
    private final double floorLogProbability;
    private final long totalQuadgrams;

    private QuadgramScorer(double[] logProbability, double floorLogProbability, long totalQuadgrams) {
        this.logProbability = logProbability;
        this.floorLogProbability = floorLogProbability;
        this.totalQuadgrams = totalQuadgrams;
    }

    /** Loads the table that ships in the jar. */
    public static QuadgramScorer fromResource() {
        try (InputStream in = QuadgramScorer.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("quadgram table missing from the classpath at " + RESOURCE);
            }
            return read(in);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read the quadgram table at " + RESOURCE, failure);
        }
    }

    /**
     * Builds a scorer from raw counts.
     *
     * <p>Quadgrams that never appeared get a floor of {@code log10(0.01 / total)} rather than
     * negative infinity, so a single unseen sequence cannot veto an otherwise excellent candidate.
     */
    public static QuadgramScorer fromCounts(int[] counts, long total) {
        if (counts.length != QUADGRAM_SPACE) {
            throw new IllegalArgumentException(
                    "counts must have " + QUADGRAM_SPACE + " entries but had " + counts.length);
        }
        if (total <= 0L) {
            throw new IllegalArgumentException("total must be positive but was " + total);
        }
        double floor = Math.log10(UNSEEN_MASS_FRACTION / total);
        double[] logProbability = new double[QUADGRAM_SPACE];
        Arrays.fill(logProbability, floor);
        for (int index = 0; index < QUADGRAM_SPACE; index++) {
            if (counts[index] > 0) {
                logProbability[index] = Math.log10((double) counts[index] / total);
            }
        }
        return new QuadgramScorer(logProbability, floor, total);
    }

    /** Reads the tab-separated table format written by {@link QuadgramTableBuilder}. */
    static QuadgramScorer read(InputStream in) throws IOException {
        int[] counts = new int[QUADGRAM_SPACE];
        long total = -1L;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                if (line.charAt(0) == '#') {
                    int marker = line.indexOf(TOTAL_HEADER_KEY);
                    if (marker >= 0) {
                        total = Long.parseLong(line.substring(marker + TOTAL_HEADER_KEY.length()).trim());
                    }
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab != QUADGRAM_LENGTH) {
                    throw new IllegalStateException("malformed quadgram line: " + line);
                }
                counts[keyIndex(line, 0)] = Integer.parseInt(line.substring(tab + 1).trim());
            }
        }
        if (total <= 0L) {
            throw new IllegalStateException("quadgram table has no positive '" + TOTAL_HEADER_KEY + "' header");
        }
        return fromCounts(counts, total);
    }

    @Override
    public double score(byte[] plaintext, int length) {
        int windows = length - (QUADGRAM_LENGTH - 1);
        if (windows <= 0) {
            return floorLogProbability * Math.max(1, length);
        }
        double sum = 0.0;
        for (int i = 0; i < windows; i++) {
            int a = LETTER_INDEX[plaintext[i] & 0xFF];
            int b = LETTER_INDEX[plaintext[i + 1] & 0xFF];
            int c = LETTER_INDEX[plaintext[i + 2] & 0xFF];
            int d = LETTER_INDEX[plaintext[i + 3] & 0xFF];
            sum += (a | b | c | d) < 0
                    ? floorLogProbability
                    : logProbability[((a * ALPHABET_SIZE + b) * ALPHABET_SIZE + c) * ALPHABET_SIZE + d];
        }
        return sum;
    }

    /** Score per window, which is comparable across messages of different lengths. */
    public double meanScore(byte[] plaintext, int length) {
        int windows = length - (QUADGRAM_LENGTH - 1);
        return windows <= 0 ? floorLogProbability : score(plaintext, length) / windows;
    }

    /** How many quadgrams the corpus behind this table contained. */
    public long totalQuadgrams() {
        return totalQuadgrams;
    }

    /** The log-probability charged to a window that is not four letters, or was never seen. */
    public double floorLogProbability() {
        return floorLogProbability;
    }

    /** Turns four letters at {@code offset} into an index into the table. */
    static int keyIndex(CharSequence quadgram, int offset) {
        int index = 0;
        for (int i = 0; i < QUADGRAM_LENGTH; i++) {
            char letter = quadgram.charAt(offset + i);
            if (letter < 'A' || letter > 'Z') {
                throw new IllegalStateException("quadgram key is not four capital letters: " + quadgram);
            }
            index = index * ALPHABET_SIZE + (letter - 'A');
        }
        return index;
    }

    /** Turns a table index back into its four letters. */
    static String quadgramOf(int index) {
        char[] letters = new char[QUADGRAM_LENGTH];
        int value = index;
        for (int position = QUADGRAM_LENGTH - 1; position >= 0; position--) {
            letters[position] = (char) ('A' + value % ALPHABET_SIZE);
            value /= ALPHABET_SIZE;
        }
        return new String(letters);
    }

    /** Letter index for one byte, or -1 if it is not a letter. */
    static int letterIndex(int unsignedByte) {
        return LETTER_INDEX[unsignedByte];
    }

    private static int[] buildLetterIndex() {
        int[] table = new int[256];
        Arrays.fill(table, -1);
        for (int letter = 0; letter < ALPHABET_SIZE; letter++) {
            table['A' + letter] = letter;
            table['a' + letter] = letter;
        }
        return table;
    }
}
