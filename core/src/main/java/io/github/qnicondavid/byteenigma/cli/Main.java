package io.github.qnicondavid.byteenigma.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;

/**
 * Command-line entry point.
 *
 * <p>Run it with no arguments for the usage text, or with {@code demo} to watch the cipher get
 * broken.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || isHelp(args[0])) {
            usage(out);
            return args.length == 0 ? 2 : 0;
        }
        String command = args[0].toLowerCase(Locale.ROOT);
        try {
            Arguments parsed = Arguments.parse(args, 1);
            return switch (command) {
                case "demo" -> DemoCommand.run(parsed, out);
                case "seal" -> CipherCommand.seal(parsed, System.in, out, err);
                case "open" -> CipherCommand.open(parsed, System.in, out, err);
                case "raw" -> CipherCommand.raw(parsed, System.in, out, err);
                case "break" -> BreakCommand.run(parsed, System.in, out);
                case "offsets" -> BreakCommand.offsets(parsed, System.in, out);
                default -> {
                    err.println("unknown command: " + command);
                    usage(err);
                    yield 2;
                }
            };
        } catch (Arguments.UsageException | IllegalArgumentException usage) {
            // The library rejects nonsense with IllegalArgumentException. From the command line
            // that is a user error with a fixable cause, not a crash, so it gets the same
            // treatment as a malformed flag rather than a stack trace.
            err.println("error: " + usage.getMessage());
            err.println("run with --help for usage");
            return 2;
        } catch (IOException failure) {
            err.println("error: " + failure.getMessage());
            return 1;
        }
    }

    private static boolean isHelp(String token) {
        return "--help".equals(token) || "-h".equals(token) || "help".equals(token);
    }

    private static void usage(PrintStream out) {
        out.println("""
                byte-enigma - a rotor cipher and the code that breaks it

                USAGE
                  byte-enigma <command> [options]

                COMMANDS
                  demo         Encipher under a secret key, then recover it two ways. Start here.
                  seal         Encrypt with a fresh nonce, writing nonce + ciphertext.
                  open         Decrypt a sealed message.
                  raw          Apply the textbook transform with no nonce. Self-inverse, and the
                               mode the breaker attacks.
                  break        Recover a key from ciphertext, by crib or by language.
                  offsets      Show which crib positions survive the no-fixed-point rule.

                KEY OPTIONS
                  --key <int>          The key, as a 32-bit integer.
                  --password <text>    Derive the key from a passphrase instead.
                  --rotors <n>         Rotor count, default 3.

                INPUT AND OUTPUT
                  --in <file>          Read from a file instead of stdin.
                  --out <file>         Write to a file instead of stdout.
                  --binary             Treat the encrypted side as raw bytes rather than Base64.
                                       Plaintext is always raw. For raw, which has no plaintext
                                       side, it applies to both input and output.
                  --nonce <long>       Seal with a chosen nonce rather than a random one.

                BREAKING
                  --crib <text>        A plaintext fragment you expect to find.
                  --at <offset>        Where you expect it.
                  --language           Ciphertext-only search, scoring English quadgrams.
                  --from <key>         First key to try, default the start of the keyspace.
                  --to <key>           One past the last, default the end of the keyspace.
                  --threads <n>        Worker threads, default one per processor.
                  --top <n>            How many candidates to keep, default 1.
                  --progress <seconds> How often to print progress, default 60 on long sweeps.
                  --checkpoint <file>  Save progress after each segment, and resume from it if the
                                       file already exists. A sweep of the whole keyspace takes
                                       hours; this is what makes an interruption cheap.
                  --segment <keys>     Keys between checkpoints, default 67108864.
                  --for <seconds>      Stop after roughly this long and checkpoint. Exit code 3
                                       means there is more range left. Run the same command again
                                       to carry on.
                  --histogram <file>   Count every score into bins and write the distribution here.
                                       Needs --language, and needs the run to start from the
                                       beginning, because it counts only the keys this run tries.

                EXAMPLES
                  byte-enigma demo
                  echo 'attack at dawn' | byte-enigma seal --password hunter2
                  byte-enigma break --crib ATTACK --at 0 --in message.b64
                  byte-enigma break --language --in message.b64 --top 5
                  byte-enigma break --language --in message.b64 --checkpoint sweep.state --for 3600
                  byte-enigma break --language --in message.b64 --histogram scores.tsv

                This cipher is a teaching artifact with a 32-bit key and no authentication.
                It is not secure and is not trying to be. See the README.""");
    }
}
