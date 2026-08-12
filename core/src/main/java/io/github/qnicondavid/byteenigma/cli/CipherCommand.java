package io.github.qnicondavid.byteenigma.cli;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import io.github.qnicondavid.byteenigma.cipher.Envelope;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** The {@code seal}, {@code open} and {@code raw} subcommands. */
final class CipherCommand {

    private CipherCommand() {
    }

    static final String[] OPTIONS = {
        "key", "password", "rotors", "in", "out", "binary", "nonce"
    };

    /** Seals a message under a fresh nonce and writes it Base64-encoded unless told otherwise. */
    static int seal(Arguments args, InputStream stdin, PrintStream stdout, PrintStream stderr) throws IOException {
        args.rejectUnknown(OPTIONS);
        ByteEnigma machine = machineFrom(args);
        byte[] plaintext = readInput(args, stdin);
        byte[] sealed = args.has("nonce")
                ? Envelope.seal(machine, plaintext, args.longValue("nonce", 0L))
                : Envelope.seal(machine, plaintext);
        writeOutput(args, stdout, sealed, args.flag("binary"));
        stderr.println("sealed " + plaintext.length + " bytes under nonce " + Envelope.nonceOf(sealed));
        return 0;
    }

    /** Opens a sealed message. */
    static int open(Arguments args, InputStream stdin, PrintStream stdout, PrintStream stderr) throws IOException {
        args.rejectUnknown(OPTIONS);
        ByteEnigma machine = machineFrom(args);
        byte[] sealed = decodeInput(args, stdin);
        if (sealed.length < Envelope.NONCE_BYTES) {
            stderr.println("input is too short to be a sealed message");
            return 2;
        }
        byte[] plaintext = Envelope.open(machine, sealed);
        writeOutput(args, stdout, plaintext, true);
        return 0;
    }

    /**
     * Applies the textbook transform: no nonce, self-inverse, and the mode the breaker attacks.
     *
     * <p>Running this twice with the same key returns the original bytes, which is convenient
     * and is also the problem.
     */
    static int raw(Arguments args, InputStream stdin, PrintStream stdout, PrintStream stderr) throws IOException {
        args.rejectUnknown(OPTIONS);
        ByteEnigma machine = machineFrom(args);
        byte[] input = args.flag("binary") ? readInput(args, stdin) : decodeInput(args, stdin);
        byte[] output = machine.transform(input);
        writeOutput(args, stdout, output, args.flag("binary"));
        stderr.println("transformed " + input.length + " bytes with no nonce; "
                + "two messages under this key will leak wherever they agree");
        return 0;
    }

    static ByteEnigma machineFrom(Arguments args) {
        int rotors = args.intValue("rotors", 3);
        if (args.has("key") && args.has("password")) {
            throw new Arguments.UsageException("give either --key or --password, not both");
        }
        if (args.has("password")) {
            return ByteEnigma.fromPassword(args.require("password"), rotors);
        }
        return new ByteEnigma(args.intValue("key", 0), rotors);
    }

    private static byte[] readInput(Arguments args, InputStream stdin) throws IOException {
        if (args.has("in")) {
            return Files.readAllBytes(Path.of(args.require("in")));
        }
        return stdin.readAllBytes();
    }

    private static byte[] decodeInput(Arguments args, InputStream stdin) throws IOException {
        if (args.flag("binary")) {
            return readInput(args, stdin);
        }
        String text = new String(readInput(args, stdin), StandardCharsets.UTF_8).trim();
        try {
            return Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException notBase64) {
            throw new Arguments.UsageException(
                    "input is not Base64; pass --binary if it is raw bytes");
        }
    }

    private static void writeOutput(Arguments args, PrintStream stdout, byte[] data, boolean binary)
            throws IOException {
        byte[] encoded = binary
                ? data
                : (Base64.getEncoder().encodeToString(data) + System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8);
        if (args.has("out")) {
            Files.write(Path.of(args.require("out")), encoded);
        } else {
            stdout.write(encoded);
            stdout.flush();
        }
    }
}
