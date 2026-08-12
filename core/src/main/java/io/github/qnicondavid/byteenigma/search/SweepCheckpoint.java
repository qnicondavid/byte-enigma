package io.github.qnicondavid.byteenigma.search;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Where a sweep got to, so it can be stopped and picked up again.
 *
 * <p>A sweep of the whole keyspace takes hours. Anything that takes hours will be interrupted:
 * a laptop lid, a full disk, an ssh session that drops. Without this, an interruption at hour
 * three costs three hours. With it, it costs whatever was left of the current segment.
 *
 * <p>The file is text, because the alternative is a binary blob you cannot inspect when it
 * disagrees with you:
 *
 * <pre>
 * # byte-enigma sweep checkpoint
 * mode=language
 * ciphertext=9f86d081884c7d65...
 * from=-2147483648
 * to=2147483648
 * cursor=-1073741824
 * keysTried=1073741824
 * elapsedNanos=3847293847293
 * best=2083951437 -412.882734 VEhFIENJUEhFUiBJTiBUSElT...
 * </pre>
 *
 * <p>The ciphertext digest is what stops a resume from silently continuing against a different
 * message: the cursor would look fine and the answer would be nonsense. Mode, range and the
 * digest all have to match or the resume is refused.
 */
public record SweepCheckpoint(
        String mode,
        String ciphertextDigest,
        long from,
        long to,
        long cursor,
        long keysTried,
        long elapsedNanos,
        List<Candidate> best) {

    private static final String HEADER = "# byte-enigma sweep checkpoint";

    public SweepCheckpoint {
        best = List.copyOf(best);
    }

    /** How much of the range is done, as a fraction. */
    public double fraction() {
        long span = to - from;
        return span <= 0L ? 1.0 : (double) (cursor - from) / span;
    }

    /** Whether the cursor has reached the end of the range. */
    public boolean isComplete() {
        return cursor >= to;
    }

    /** Keys per second across every segment run so far, not just the last one. */
    public double keysPerSecond() {
        double seconds = elapsedNanos / 1_000_000_000.0;
        return seconds > 0.0 ? keysTried / seconds : 0.0;
    }

    /** SHA-256 of the ciphertext, hex, so a resume cannot drift onto a different message. */
    public static String digestOf(byte[] ciphertext) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ciphertext);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM has SHA-256", impossible);
        }
    }

    /**
     * Writes the checkpoint to a temporary file and moves it into place, so an interruption
     * during the write leaves the previous checkpoint intact rather than half of a new one.
     */
    public void save(Path path) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append(HEADER).append('\n');
        out.append("mode=").append(mode).append('\n');
        out.append("ciphertext=").append(ciphertextDigest).append('\n');
        out.append("from=").append(from).append('\n');
        out.append("to=").append(to).append('\n');
        out.append("cursor=").append(cursor).append('\n');
        out.append("keysTried=").append(keysTried).append('\n');
        out.append("elapsedNanos=").append(elapsedNanos).append('\n');
        for (Candidate candidate : best) {
            out.append("best=").append(candidate.key()).append(' ')
                    .append(candidate.score()).append(' ')
                    .append(Base64.getEncoder().encodeToString(candidate.plaintext()))
                    .append('\n');
        }
        Path directory = path.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(directory, "sweep", ".checkpoint");
        Files.writeString(temporary, out.toString(), StandardCharsets.UTF_8);
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Reads a checkpoint, or returns {@code null} if the file is not there. */
    public static SweepCheckpoint load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        String mode = null;
        String digest = null;
        long from = 0;
        long to = 0;
        long cursor = 0;
        long keysTried = 0;
        long elapsedNanos = 0;
        List<Candidate> best = new ArrayList<>();

        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                throw new IOException("malformed checkpoint line: " + line);
            }
            String name = line.substring(0, equals);
            String value = line.substring(equals + 1).trim();
            switch (name) {
                case "mode" -> mode = value;
                case "ciphertext" -> digest = value;
                case "from" -> from = Long.parseLong(value);
                case "to" -> to = Long.parseLong(value);
                case "cursor" -> cursor = Long.parseLong(value);
                case "keysTried" -> keysTried = Long.parseLong(value);
                case "elapsedNanos" -> elapsedNanos = Long.parseLong(value);
                case "best" -> best.add(parseCandidate(value));
                default -> throw new IOException("unknown checkpoint field: " + name);
            }
        }
        if (mode == null || digest == null) {
            throw new IOException("checkpoint is missing its mode or its ciphertext digest: " + path);
        }
        return new SweepCheckpoint(mode, digest, from, to, cursor, keysTried, elapsedNanos, best);
    }

    /**
     * Checks that a checkpoint describes the sweep about to be run.
     *
     * @throws IOException with a message naming the field that disagrees
     */
    public void requireMatches(String expectedMode, String expectedDigest, long expectedFrom, long expectedTo)
            throws IOException {
        if (!mode.equals(expectedMode)) {
            throw new IOException("checkpoint is for mode " + mode + ", not " + expectedMode);
        }
        if (!ciphertextDigest.equals(expectedDigest)) {
            throw new IOException("checkpoint is for a different ciphertext");
        }
        if (from != expectedFrom || to != expectedTo) {
            throw new IOException("checkpoint covers [" + from + ", " + to + "), not ["
                    + expectedFrom + ", " + expectedTo + ")");
        }
    }

    private static Candidate parseCandidate(String value) throws IOException {
        String[] parts = value.split(" ", 3);
        if (parts.length != 3) {
            throw new IOException("malformed candidate in checkpoint: " + value);
        }
        try {
            return Candidate.of(Integer.parseInt(parts[0]), Double.parseDouble(parts[1]),
                    Base64.getDecoder().decode(parts[2]));
        } catch (IllegalArgumentException malformed) {
            throw new IOException("malformed candidate in checkpoint: " + value, malformed);
        }
    }
}
