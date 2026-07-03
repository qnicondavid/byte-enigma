package com.enigma.breaker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class QuadgramScorer implements PlaintextScorer {

    public static final String RESOURCE = "/com/enigma/breaker/quadgrams.txt";

    static final int ALPHABET_SIZE = 26;
    static final int QUADGRAM_SPACE = ALPHABET_SIZE * ALPHABET_SIZE * ALPHABET_SIZE * ALPHABET_SIZE;
    static final int QUADGRAM_LENGTH = 4;

    private static final double UNSEEN_MASS_FRACTION = 0.01;
    private static final String TOTAL_HEADER_KEY = "total=";
    private static final int INITIAL_SCRATCH = 256;

    private final double[] logProbability;
    private final double floorLogProbability;
    private final long totalQuadgrams;

    private final ThreadLocal<byte[]> scratch =
            ThreadLocal.withInitial(() -> new byte[INITIAL_SCRATCH]);

    private QuadgramScorer(double[] logProbability, double floorLogProbability, long totalQuadgrams) {
        this.logProbability = logProbability;
        this.floorLogProbability = floorLogProbability;
        this.totalQuadgrams = totalQuadgrams;
    }

    public static QuadgramScorer fromResource() {
        try (InputStream in = QuadgramScorer.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing quadgram resource on classpath: " + RESOURCE);
            }
            return read(in);
        } catch (IOException failure) {
            throw new UncheckedIOException("unable to load quadgram resource: " + RESOURCE, failure);
        }
    }

    public static QuadgramScorer fromCounts(int[] counts, long total) {
        if (counts.length != QUADGRAM_SPACE) {
            throw new IllegalArgumentException("counts length must be " + QUADGRAM_SPACE + " but was " + counts.length);
        }
        if (total <= 0) {
            throw new IllegalArgumentException("total must be positive but was " + total);
        }
        double floor = Math.log10(UNSEEN_MASS_FRACTION / total);
        double[] logProbability = new double[QUADGRAM_SPACE];
        Arrays.fill(logProbability, floor);
        for (int index = 0; index < QUADGRAM_SPACE; index++) {
            int count = counts[index];
            if (count > 0) {
                logProbability[index] = Math.log10((double) count / total);
            }
        }
        return new QuadgramScorer(logProbability, floor, total);
    }

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
                counts[keyIndex(line.substring(0, tab))] = Integer.parseInt(line.substring(tab + 1).trim());
            }
        }
        if (total <= 0) {
            throw new IllegalStateException("quadgram resource is missing a positive '" + TOTAL_HEADER_KEY + "' header");
        }
        return fromCounts(counts, total);
    }

    @Override
    public double score(byte[] plaintext, int len) {
        byte[] letters = scratchFor(len);
        int count = normalize(plaintext, len, letters);
        return accumulate(letters, count);
    }

    public double score(byte[] decrypted) {
        return score(decrypted, decrypted.length);
    }

    public double meanScore(byte[] decrypted) {
        return meanScore(decrypted, decrypted.length);
    }

    public double meanScore(byte[] decrypted, int len) {
        byte[] letters = scratchFor(len);
        int count = normalize(decrypted, len, letters);
        int quadgrams = count - (QUADGRAM_LENGTH - 1);
        if (quadgrams <= 0) {
            return floorLogProbability;
        }
        return accumulate(letters, count) / quadgrams;
    }

    public long totalQuadgrams() {
        return totalQuadgrams;
    }

    public double floorLogProbability() {
        return floorLogProbability;
    }

    private double accumulate(byte[] letters, int count) {
        double sum = 0.0;
        int last = count - QUADGRAM_LENGTH;
        for (int i = 0; i <= last; i++) {
            int index = ((letters[i] * ALPHABET_SIZE + letters[i + 1]) * ALPHABET_SIZE
                    + letters[i + 2]) * ALPHABET_SIZE + letters[i + 3];
            sum += logProbability[index];
        }
        return sum;
    }

    private byte[] scratchFor(int len) {
        byte[] buffer = scratch.get();
        if (buffer.length < len) {
            buffer = new byte[Integer.highestOneBit(len - 1) << 1];
            scratch.set(buffer);
        }
        return buffer;
    }

    static int normalize(byte[] data, int len, byte[] destination) {
        String upper = decode(data, len).toUpperCase(Locale.ROOT);
        int written = 0;
        for (int i = 0; i < upper.length() && written < destination.length; i++) {
            char letter = upper.charAt(i);
            if (letter >= 'A' && letter <= 'Z') {
                destination[written++] = (byte) (letter - 'A');
            }
        }
        return written;
    }

    private static String decode(byte[] data, int len) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer decoded = decoder.decode(ByteBuffer.wrap(data, 0, len));
            return decoded.toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(data, 0, len, StandardCharsets.ISO_8859_1);
        }
    }

    private static int keyIndex(String quadgram) {
        int index = 0;
        for (int i = 0; i < QUADGRAM_LENGTH; i++) {
            char letter = quadgram.charAt(i);
            if (letter < 'A' || letter > 'Z') {
                throw new IllegalStateException("non-letter in quadgram key: " + quadgram);
            }
            index = index * ALPHABET_SIZE + (letter - 'A');
        }
        return index;
    }
}
