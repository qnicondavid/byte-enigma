package io.github.qnicondavid.byteenigma.breaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shipped quadgram table must be exactly what the committed corpus produces.
 *
 * <p>A derived file whose source is missing is a binary blob with a text extension. This test is
 * the thing that stops that happening again: change the corpus without regenerating, or
 * regenerate without committing, and the build stops.
 */
class QuadgramTableReproducibilityTest {

    @Test
    void theShippedTableIsExactlyWhatTheCorpusProduces() throws IOException {
        Path root = QuadgramTableBuilder.projectRoot();
        List<Path> corpus = QuadgramTableBuilder.corpusFiles(root.resolve(QuadgramTableBuilder.CORPUS_DIRECTORY));
        String regenerated = QuadgramTableBuilder.render(QuadgramTableBuilder.countCorpus(corpus));
        String shipped = Files.readString(root.resolve(QuadgramTableBuilder.OUTPUT_PATH), StandardCharsets.UTF_8);
        assertEquals(shipped, regenerated,
                "the shipped table no longer matches the corpus; run QuadgramTableBuilder and commit both");
    }

    @Test
    void theTableOnTheClasspathIsTheOneOnDisk() throws IOException {
        Path root = QuadgramTableBuilder.projectRoot();
        String onDisk = Files.readString(root.resolve(QuadgramTableBuilder.OUTPUT_PATH), StandardCharsets.UTF_8);
        try (InputStream in = QuadgramScorer.class.getResourceAsStream(QuadgramScorer.RESOURCE)) {
            assertTrue(in != null, "the table is missing from the classpath");
            assertEquals(onDisk, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theCorpusIsPresentAndAccountedFor() throws IOException {
        Path root = QuadgramTableBuilder.projectRoot();
        Path corpusDirectory = root.resolve(QuadgramTableBuilder.CORPUS_DIRECTORY);
        List<Path> files = QuadgramTableBuilder.corpusFiles(corpusDirectory);
        assertTrue(files.size() >= 5, "expected several corpus files, found " + files.size());
        assertTrue(Files.isRegularFile(corpusDirectory.resolve("MANIFEST.md")),
                "the corpus needs a MANIFEST recording where each text came from");
        String manifest = Files.readString(corpusDirectory.resolve("MANIFEST.md"), StandardCharsets.UTF_8);
        for (Path file : files) {
            assertTrue(manifest.contains(file.getFileName().toString()),
                    file.getFileName() + " is not recorded in the MANIFEST");
        }
    }

    @Test
    void countingOnlyChargesWindowsTheScorerWouldCharge() {
        int[] counts = new int[QuadgramScorer.QUADGRAM_SPACE];
        long windows = QuadgramTableBuilder.countInto(
                "AB CD".getBytes(StandardCharsets.UTF_8), counts);
        assertEquals(0L, windows, "a window straddling a space must not be counted");

        long inWord = QuadgramTableBuilder.countInto(
                "ABCDE".getBytes(StandardCharsets.UTF_8), counts);
        assertEquals(2L, inWord, "five letters give two windows");
    }
}
