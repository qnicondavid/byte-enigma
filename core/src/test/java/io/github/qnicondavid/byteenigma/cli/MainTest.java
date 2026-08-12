package io.github.qnicondavid.byteenigma.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The command line answers for itself, and says so when it cannot. */
class MainTest {

    private record Run(int status, String out, String err) {
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
            {"break", "--language", "--threads", "0", "--in", "/dev/null"},
            {"break", "--language", "--from", "5", "--to", "1", "--in", "/dev/null"},
            {"break", "--language", "--from", "0", "--to", "99999999999", "--in", "/dev/null"},
        };
        for (String[] arguments : bad) {
            Run run = invoke(arguments);
            assertEquals(2, run.status(), String.join(" ", arguments));
            assertTrue(run.err().startsWith("error: "), run.err());
        }
    }

    @Test
    void breakingNeedsExactlyOneMode() {
        Run neither = invoke("break", "--in", "/dev/null");
        assertEquals(2, neither.status());
        assertTrue(neither.err().contains("exactly one"), neither.err());
    }

    @Test
    void aCribWithoutAnOffsetIsRefusedWithAdvice() {
        Run run = invoke("break", "--crib", "ATTACK", "--in", "/dev/null");
        assertEquals(2, run.status());
        assertTrue(run.err().contains("--at"), run.err());
    }
}
