package io.github.qnicondavid.byteenigma.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
