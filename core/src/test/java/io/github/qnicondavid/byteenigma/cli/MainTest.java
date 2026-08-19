package io.github.qnicondavid.byteenigma.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The command line answers for itself, and says so when it cannot. */
class MainTest {

    private record Run(int status, String out, String err) {
    }

    /**
     * An input file that exists and is empty. The break command reads {@code --in} before it
     * validates the rest of the arguments, so a path that does not resolve fails as unreadable
     * input rather than as the usage error under test. A hardcoded {@code /dev/null} passed on
     * Linux and turned three of these assertions red on Windows.
     */
    private static final String EMPTY_INPUT = emptyInput();

    private static String emptyInput() {
        try {
            Path file = Files.createTempFile("byte-enigma-empty", ".b64");
            file.toFile().deleteOnExit();
            return file.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Run invoke(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = Main.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void noArgumentsPrintsUsageAndFails() {
        Run run = invoke();
        assertEquals(2, run.status());
        assertTrue(run.out().contains("USAGE"), run.out());
    }

    @Test
    void helpPrintsUsageAndSucceeds() {
        for (String flag : new String[] {"--help", "-h", "help"}) {
            Run run = invoke(flag);
            assertEquals(0, run.status(), "status for " + flag);
            assertTrue(run.out().contains("byte-enigma"), run.out());
        }
    }

    @Test
    void usageSaysTheCipherIsNotSecure() {
        assertTrue(invoke("--help").out().contains("not secure"),
                "the usage text must not let anyone mistake this for a real cipher");
    }

    @Test
    void anUnknownCommandFails() {
        Run run = invoke("encipher");
        assertEquals(2, run.status());
        assertTrue(run.err().contains("unknown command"), run.err());
    }

    @Test
    void anUnknownOptionFailsRatherThanBeingIgnored() {
        Run run = invoke("demo", "--thredz", "2");
        assertEquals(2, run.status());
        assertTrue(run.err().contains("unknown option"), run.err());
    }

    @Test
    void sealingAndOpeningRoundTripThroughAFile() throws Exception {
        Path plain = Files.createTempFile("byte-enigma", ".txt");
        Path sealed = Files.createTempFile("byte-enigma", ".b64");
        try {
            Files.writeString(plain, "attack at dawn", StandardCharsets.UTF_8);
            assertEquals(0, invoke("seal", "--password", "hunter2",
                    "--in", plain.toString(), "--out", sealed.toString()).status());

            Run opened = invoke("open", "--password", "hunter2", "--in", sealed.toString());
            assertEquals(0, opened.status());
            assertEquals("attack at dawn", opened.out());
        } finally {
            Files.deleteIfExists(plain);
            Files.deleteIfExists(sealed);
        }
    }

    @Test
    void openingAcceptsBase64ThatHasBeenWrappedAcrossLines() throws Exception {
        Path plain = Files.createTempFile("byte-enigma", ".txt");
        Path sealed = Files.createTempFile("byte-enigma", ".b64");
        try {
            Files.writeString(plain, "attack at dawn", StandardCharsets.UTF_8);
            assertEquals(0, invoke("seal", "--password", "hunter2",
                    "--in", plain.toString(), "--out", sealed.toString()).status());

            String oneLine = Files.readString(sealed, StandardCharsets.UTF_8).strip();
            StringBuilder wrapped = new StringBuilder();
            for (int at = 0; at < oneLine.length(); at += 20) {
                wrapped.append(oneLine, at, Math.min(at + 20, oneLine.length())).append('\n');
            }
            Files.writeString(sealed, wrapped.toString(), StandardCharsets.UTF_8);

            Run opened = invoke("open", "--password", "hunter2", "--in", sealed.toString());
            assertEquals(0, opened.status(), opened.err());
            assertEquals("attack at dawn", opened.out());
        } finally {
            Files.deleteIfExists(plain);
            Files.deleteIfExists(sealed);
        }
    }

    @Test
    void aFileOfProseIsStillNotBase64() throws Exception {
        Path prose = Files.createTempFile("byte-enigma", ".txt");
        try {
            Files.writeString(prose, "this is not a ciphertext, it is a sentence.\n",
                    StandardCharsets.UTF_8);
            Run run = invoke("open", "--password", "hunter2", "--in", prose.toString());
            assertEquals(2, run.status());
            assertTrue(run.err().contains("not Base64"), run.err());
        } finally {
            Files.deleteIfExists(prose);
        }
    }

    @Test
    void sealingTheSameMessageTwiceGivesDifferentCiphertext() throws Exception {
        Path plain = Files.createTempFile("byte-enigma", ".txt");
        try {
            Files.writeString(plain, "attack at dawn", StandardCharsets.UTF_8);
            String first = invoke("seal", "--password", "hunter2", "--in", plain.toString()).out();
            String second = invoke("seal", "--password", "hunter2", "--in", plain.toString()).out();
            assertTrue(!first.equals(second), "seal must draw a fresh nonce each time");
        } finally {
            Files.deleteIfExists(plain);
        }
    }

    @Test
    void rawIsItsOwnInverseAndWarnsAboutReuse() throws Exception {
        Path plain = Files.createTempFile("byte-enigma", ".txt");
        Path cipher = Files.createTempFile("byte-enigma", ".bin");
        try {
            Files.writeString(plain, "ATTACK AT DAWN", StandardCharsets.UTF_8);
            Run first = invoke("raw", "--binary", "--key", "99",
                    "--in", plain.toString(), "--out", cipher.toString());
            assertEquals(0, first.status());
            assertTrue(first.err().contains("leak"), first.err());

            Run second = invoke("raw", "--binary", "--key", "99", "--in", cipher.toString());
            assertEquals("ATTACK AT DAWN", second.out());
        } finally {
            Files.deleteIfExists(plain);
            Files.deleteIfExists(cipher);
        }
    }

    /**
     * The command line prints the same numbers whatever the machine is set to. Under a
     * comma-decimal locale an unqualified {@code printf} turns 2.8% into 2,8% and 314,762 into
     * 314.762, and a reader could not compare their own run against the log in
     * {@code docs/keyspace-sweep.md}. This runs one deterministic command twice and asks for the
     * same bytes back. The default locale is restored afterwards, which is safe because nothing
     * here configures surefire or JUnit to run tests in parallel.
     */
    @Test
    void theOutputDoesNotMoveWithTheDefaultLocale() throws Exception {
        Path plain = Files.createTempFile("byte-enigma", ".txt");
        Path cipher = Files.createTempFile("byte-enigma", ".bin");
        Locale original = Locale.getDefault();
        try {
            Files.writeString(plain, "ATTACK AT DAWN AND HOLD THE COAST UNTIL THE FLEET ARRIVES",
                    StandardCharsets.UTF_8);
            assertEquals(0, invoke("raw", "--binary", "--key", "99",
                    "--in", plain.toString(), "--out", cipher.toString()).status());

            String[] offsets = {"offsets", "--crib", "ATTACK AT DAWN", "--binary",
                "--in", cipher.toString()};
            Locale.setDefault(Locale.ROOT);
            Run root = invoke(offsets);
            Locale.setDefault(Locale.forLanguageTag("ro-RO"));
            Run comma = invoke(offsets);

            assertEquals(0, root.status(), root.err());
            assertEquals(0, comma.status(), comma.err());
            assertTrue(root.out().contains("eliminated:"), root.out());
            assertEquals(root.out(), comma.out(),
                    "the offsets report is not the same on a comma-decimal machine");
        } finally {
            Locale.setDefault(original);
            Files.deleteIfExists(plain);
            Files.deleteIfExists(cipher);
        }
    }

    @Test
    void givingBothAKeyAndAPassphraseIsRefused() {
        Run run = invoke("raw", "--key", "1", "--password", "hunter2");
        assertEquals(2, run.status());
        assertTrue(run.err().contains("not both"), run.err());
    }

    @Test
    void argumentsTheLibraryRejectsComeBackAsUsageErrorsRatherThanStackTraces() {
        String[][] bad = {
            {"raw", "--key", "1", "--rotors", "0"},
            {"break", "--language", "--threads", "0", "--in", EMPTY_INPUT},
            {"break", "--language", "--from", "5", "--to", "1", "--in", EMPTY_INPUT},
            {"break", "--language", "--from", "0", "--to", "99999999999", "--in", EMPTY_INPUT},
        };
        for (String[] arguments : bad) {
            Run run = invoke(arguments);
            assertEquals(2, run.status(), String.join(" ", arguments));
            assertTrue(run.err().startsWith("error: "), run.err());
        }
    }

    @Test
    void breakingNeedsExactlyOneMode() {
        Run neither = invoke("break", "--in", EMPTY_INPUT);
        assertEquals(2, neither.status());
        assertTrue(neither.err().contains("exactly one"), neither.err());
    }

    @Test
    void aCribWithoutAnOffsetIsRefusedWithAdvice() {
        Run run = invoke("break", "--crib", "ATTACK", "--in", EMPTY_INPUT);
        assertEquals(2, run.status());
        assertTrue(run.err().contains("--at"), run.err());
    }
}
