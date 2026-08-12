package io.github.qnicondavid.byteenigma.cipher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The crib attack decrypts a window rather than a message, which is only sound if a window is
 * bit for bit what the full transform would have written there.
 */
class TransformWindowTest {

    private static byte[] input(int length) {
        byte[] input = new byte[length];
        for (int i = 0; i < length; i++) {
            input[i] = (byte) ((i * 31 + 7) & 0xFF);
        }
        return input;
    }

    @Test
    void aWindowMatchesTheFullTransformOverThatRange() {
        byte[] input = input(300);
        for (int rotorCount = 1; rotorCount <= 4; rotorCount++) {
            ByteEnigma machine = new ByteEnigma(4242, rotorCount);
            byte[] full = machine.transform(input);
            for (int from : new int[] {0, 1, 17, 128, 255, 256, 257, 299}) {
                for (int length : new int[] {1, 2, 16, 44}) {
                    int to = Math.min(from + length, input.length);
                    byte[] window = new byte[input.length];
                    assertEquals(to - from, machine.transformWindow(input, window, from, to));
                    for (int i = from; i < to; i++) {
                        assertEquals(full[i], window[i],
                                "byte " + i + " differs for window [" + from + ", " + to
                                        + ") at rotors " + rotorCount);
                    }
                }
            }
        }
    }

    @Test
    void aWindowLeavesEverythingOutsideItAlone() {
        byte[] input = input(120);
        ByteEnigma machine = new ByteEnigma(7, 3);
        byte[] output = new byte[input.length];
        Arrays.fill(output, (byte) 0x5A);
        machine.transformWindow(input, output, 40, 60);
        for (int i = 0; i < input.length; i++) {
            if (i < 40 || i >= 60) {
                assertEquals((byte) 0x5A, output[i], "byte " + i + " was written outside the window");
            }
        }
    }

    @Test
    void aNoncedWindowMatchesTheFullNoncedTransform() {
        byte[] input = input(200);
        ByteEnigma machine = new ByteEnigma(-99, 3);
        byte[] full = machine.transform(input, 31337L);
        byte[] window = new byte[input.length];
        machine.transformWindow(input, window, 50, 90, 31337L);
        for (int i = 50; i < 90; i++) {
            assertEquals(full[i], window[i], "byte " + i + " differs");
        }
    }

    @Test
    void anEmptyWindowIsLegalAndWritesNothing() {
        ByteEnigma machine = new ByteEnigma(1, 3);
        assertEquals(0, machine.transformWindow(input(10), new byte[10], 5, 5));
    }

    @Test
    void impossibleWindowsAreRejected() {
        ByteEnigma machine = new ByteEnigma(1, 3);
        byte[] input = input(10);
        for (int[] range : new int[][] {{-1, 5}, {6, 5}, {0, 11}, {11, 12}}) {
            try {
                machine.transformWindow(input, new byte[10], range[0], range[1]);
                assertTrue(false, "expected rejection of [" + range[0] + ", " + range[1] + ")");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("window"));
            }
        }
    }
}
