package io.github.qnicondavid.byteenigma.search;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A checkpoint exists so that an interruption three hours into a four-hour sweep costs minutes
 * rather than three hours. That is only true if it round-trips exactly and refuses to resume onto
 * something it does not describe.
 */
class SweepCheckpointTest {

    private static final byte[] CIPHERTEXT = "a message under attack".getBytes(StandardCharsets.UTF_8);

    private static SweepCheckpoint sample(long cursor) {
        return new SweepCheckpoint("language", SweepCheckpoint.digestOf(CIPHERTEXT),
                -2147483648L, 2147483648L, cursor, cursor + 2147483648L, 1_000_000_000L,
                List.of(Candidate.of(42, -412.5, "recovered plaintext".getBytes(StandardCharsets.UTF_8)),
                        Candidate.of(-7, -900.25, new byte[] {0, 1, 2, (byte) 0xFF})));
    }

    private static Path temporaryFile() throws IOException {
        Path path = Files.createTempFile("checkpoint", ".state");
        Files.delete(path);
        return path;
    }

    @Test
    void roundTripsThroughAFile() throws IOException {
        Path path = temporaryFile();
        try {
            SweepCheckpoint written = sample(0L);
            written.save(path);
            SweepCheckpoint read = SweepCheckpoint.load(path);

            assertEquals(written.mode(), read.mode());
            assertEquals(written.ciphertextDigest(), read.ciphertextDigest());
            assertEquals(written.from(), read.from());
            assertEquals(written.to(), read.to());
            assertEquals(written.cursor(), read.cursor());
            assertEquals(written.keysTried(), read.keysTried());
            assertEquals(written.elapsedNanos(), read.elapsedNanos());
            assertEquals(2, read.best().size());
            assertEquals(42, read.best().get(0).key());
            assertEquals(-412.5, read.best().get(0).score(), 0.0);
            assertArrayEquals("recovered plaintext".getBytes(StandardCharsets.UTF_8),
                    read.best().get(0).plaintext());
            assertArrayEquals(new byte[] {0, 1, 2, (byte) 0xFF}, read.best().get(1).plaintext());
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void aScoreSurvivesToTheLastBit() throws IOException {
        Path path = temporaryFile();
        try {
            double awkward = -412.88273400391234;
            new SweepCheckpoint("language", "abc", 0, 10, 5, 5, 1,
                    List.of(Candidate.of(1, awkward, new byte[] {7}))).save(path);
            assertEquals(awkward, SweepCheckpoint.load(path).best().get(0).score(), 0.0,
                    "a rounded score would reorder candidates on resume");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void aMissingFileIsNotAnError() throws IOException {
        assertNull(SweepCheckpoint.load(Path.of("/nonexistent/checkpoint.state")));
    }

    @Test
    void resumingOntoADifferentCiphertextIsRefused() throws IOException {
        SweepCheckpoint checkpoint = sample(0L);
        String otherDigest = SweepCheckpoint.digestOf("a different message".getBytes(StandardCharsets.UTF_8));
        assertRefused(() -> checkpoint.requireMatches("language", otherDigest, -2147483648L, 2147483648L),
                "different ciphertext");
    }

    @Test
    void resumingOntoADifferentModeIsRefused() {
        SweepCheckpoint checkpoint = sample(0L);
        assertRefused(() -> checkpoint.requireMatches("crib:36:18", checkpoint.ciphertextDigest(),
                -2147483648L, 2147483648L), "mode");
    }

    @Test
    void resumingOntoADifferentRangeIsRefused() {
        SweepCheckpoint checkpoint = sample(0L);
        assertRefused(() -> checkpoint.requireMatches("language", checkpoint.ciphertextDigest(), 0L, 1000L),
                "covers");
    }

    @Test
    void aMatchingCheckpointIsAccepted() throws IOException {
        SweepCheckpoint checkpoint = sample(0L);
        checkpoint.requireMatches("language", checkpoint.ciphertextDigest(), -2147483648L, 2147483648L);
    }

    @Test
    void reportsHowFarThroughTheRangeItIs() {
        assertEquals(0.5, sample(0L).fraction(), 1e-12);
        assertTrue(!sample(0L).isComplete());
        assertTrue(sample(2147483648L).isComplete());
    }

    @Test
    void ratesAreAveragedAcrossEverySegmentRunSoFar() {
        SweepCheckpoint checkpoint = new SweepCheckpoint("language", "abc", 0, 1000, 500, 500,
                2_000_000_000L, List.of());
        assertEquals(250.0, checkpoint.keysPerSecond(), 1e-9);
    }

    @Test
    void anUnknownFieldIsRejectedRatherThanIgnored() throws IOException {
        Path path = temporaryFile();
        try {
            Files.writeString(path, "mode=language\nciphertext=abc\nsurprise=42\n");
            assertRefused(() -> SweepCheckpoint.load(path), "unknown checkpoint field");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void aTruncatedCheckpointIsRejected() throws IOException {
        Path path = temporaryFile();
        try {
            Files.writeString(path, "# byte-enigma sweep checkpoint\nfrom=0\nto=10\n");
            assertRefused(() -> SweepCheckpoint.load(path), "missing its mode");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void overwritingAnExistingCheckpointKeepsItReadable() throws IOException {
        Path path = temporaryFile();
        try {
            sample(0L).save(path);
            sample(1_000_000L).save(path);
            assertEquals(1_000_000L, SweepCheckpoint.load(path).cursor());
            try (var entries = Files.list(path.toAbsolutePath().getParent())) {
                assertTrue(entries.noneMatch(p -> p.getFileName().toString().startsWith("sweep")
                                && p.getFileName().toString().endsWith(".checkpoint")),
                        "the temporary file used for the atomic move was left behind");
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @FunctionalInterface
    private interface Failing {
        void run() throws IOException;
    }

    private static void assertRefused(Failing action, String expectedInMessage) {
        try {
            action.run();
            assertTrue(false, "expected an IOException mentioning " + expectedInMessage);
        } catch (IOException refused) {
            assertTrue(refused.getMessage().contains(expectedInMessage),
                    "message was: " + refused.getMessage());
        }
    }
}
