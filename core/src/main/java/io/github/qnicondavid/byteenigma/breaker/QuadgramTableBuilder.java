package io.github.qnicondavid.byteenigma.breaker;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rebuilds the shipped quadgram table from the corpus in {@code data/corpus}.
 *
 * <p>The table is a derived file. Derived files that cannot be re-derived are just binaries with
 * extra steps, so the corpus they come from is committed alongside them and
 * {@code QuadgramTableReproducibilityTest} fails the build if the shipped table stops matching
 * what this class produces. Change the corpus, run {@code main}, commit both.
 *
 * <h2>Which windows get counted</h2>
 *
 * <p>Exactly the ones {@link QuadgramScorer} will charge: four consecutive letters in the source
 * text, case folded. A window that straddles a space or a comma is not counted here, because it
 * would never be looked up there either. Counting letters-only, with the spaces squeezed out,
 * is the more common recipe and manufactures quadgrams like {@code THEQ} that no scorer will
 * ever ask about.
 *
 * <p>Quadgrams seen fewer than {@link #PRUNE_MIN_COUNT} times are dropped. They carry almost no
 * signal and most of the file size.
 */
public final class QuadgramTableBuilder {

    /** Counts below this are noise from a corpus this size, and are left out of the table. */
    public static final int PRUNE_MIN_COUNT = 3;

    /** Where the corpus lives, relative to the project root. */
    public static final String CORPUS_DIRECTORY = "data/corpus";

    /** Where the generated table is written, relative to the project root. */
    public static final String OUTPUT_PATH =
            "core/src/main/resources/io/github/qnicondavid/byteenigma/breaker/quadgrams.txt";

    private QuadgramTableBuilder() {
    }

    /** Counts and their total, before pruning. */
    public record Counts(int[] counts, long total) {

        /** How many distinct quadgrams survive pruning. */
        public int kept() {
            int kept = 0;
            for (int count : counts) {
                if (count >= PRUNE_MIN_COUNT) {
                    kept++;
                }
            }
            return kept;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = projectRoot();
        Path corpus = args.length > 0 ? Path.of(args[0]) : root.resolve(CORPUS_DIRECTORY);
        Path output = root.resolve(OUTPUT_PATH);

        List<Path> files = corpusFiles(corpus);
        Counts counts = countCorpus(files);
        Files.createDirectories(output.getParent());
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            write(writer, counts);
        }

        System.out.println("corpus:  " + files.size() + " files under " + corpus);
        for (Path file : files) {
            System.out.println("         " + file.getFileName() + "  " + Files.size(file) + " bytes");
        }
        System.out.println("windows: " + counts.total());
        System.out.println("kept:    " + counts.kept() + " quadgrams seen at least " + PRUNE_MIN_COUNT + " times");
        System.out.println("wrote:   " + output);
    }

    /** Every {@code .txt} file in the corpus directory, in a stable order. */
    public static List<Path> corpusFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("corpus directory not found: " + directory.toAbsolutePath());
        }
        try (Stream<Path> entries = Files.list(directory)) {
            List<Path> files = new ArrayList<>(entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .toList());
            files.sort(Comparator.comparing(path -> path.getFileName().toString()));
            if (files.isEmpty()) {
                throw new IOException("no .txt files in corpus directory: " + directory.toAbsolutePath());
            }
            return files;
        }
    }

    /** Counts every all-letter quadgram window across the given files. */
    public static Counts countCorpus(List<Path> files) throws IOException {
        int[] counts = new int[QuadgramScorer.QUADGRAM_SPACE];
        long total = 0L;
        for (Path file : files) {
            total += countInto(Files.readAllBytes(file), counts);
        }
        return new Counts(counts, total);
    }

    /** Counts one buffer into an existing table, returning how many windows it contributed. */
    static long countInto(byte[] text, int[] counts) {
        long total = 0L;
        int last = text.length - QuadgramScorer.QUADGRAM_LENGTH;
        for (int i = 0; i <= last; i++) {
            int a = QuadgramScorer.letterIndex(text[i] & 0xFF);
            int b = QuadgramScorer.letterIndex(text[i + 1] & 0xFF);
            int c = QuadgramScorer.letterIndex(text[i + 2] & 0xFF);
            int d = QuadgramScorer.letterIndex(text[i + 3] & 0xFF);
            if ((a | b | c | d) >= 0) {
                counts[((a * QuadgramScorer.ALPHABET_SIZE + b) * QuadgramScorer.ALPHABET_SIZE + c)
                        * QuadgramScorer.ALPHABET_SIZE + d]++;
                total++;
            }
        }
        return total;
    }

    /** Writes the table in the format {@link QuadgramScorer#read} expects, with LF line endings. */
    static void write(Writer writer, Counts counts) throws IOException {
        writer.write("# byte-enigma quadgram table\n");
        writer.write("# generated by QuadgramTableBuilder from data/corpus; do not edit by hand\n");
        writer.write("# counts of all-letter 4-grams, case folded, pruned to count>="
                + PRUNE_MIN_COUNT + "; total=" + counts.total() + "\n");
        int[] table = counts.counts();
        for (int index = 0; index < table.length; index++) {
            if (table[index] >= PRUNE_MIN_COUNT) {
                writer.write(QuadgramScorer.quadgramOf(index));
                writer.write('\t');
                writer.write(Integer.toString(table[index]));
                writer.write('\n');
            }
        }
    }

    /** Renders a table to the exact text that belongs in the resource file. */
    public static String render(Counts counts) {
        StringWriter out = new StringWriter();
        try {
            write(out, counts);
        } catch (IOException impossible) {
            throw new IllegalStateException("StringWriter does not throw", impossible);
        }
        return out.toString();
    }

    /**
     * Finds the project root by walking up from the working directory looking for a {@code pom.xml}
     * next to a {@code data/corpus} directory.
     *
     * <p>Override with {@code -Dbyteenigma.projectRoot=...} if you are running from somewhere odd.
     */
    public static Path projectRoot() {
        String override = System.getProperty("byteenigma.projectRoot");
        if (override != null) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve(CORPUS_DIRECTORY))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "could not find the project root above " + Path.of("").toAbsolutePath()
                        + "; pass -Dbyteenigma.projectRoot=<path>");
    }
}
