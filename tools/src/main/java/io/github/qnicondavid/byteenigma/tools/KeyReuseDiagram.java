package io.github.qnicondavid.byteenigma.tools;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;
import java.nio.charset.StandardCharsets;

/**
 * What an eavesdropper gets from two messages sent under one key.
 *
 * <p>The picture is not drawn from a remembered number. This runs the cipher over both messages
 * under one key, compares the two ciphertexts byte by byte, and lights the positions that agree.
 * Then it checks that those are exactly the positions where the two plaintexts agree, which is the
 * claim the whole figure makes, and refuses to draw anything if that fails.
 *
 * <p>The two messages are the ones {@code DemoCommand} uses, so the count here and the count the
 * demo prints are about the same pair.
 */
final class KeyReuseDiagram {

    private static final String FIRST =
            "THE ENEMY FLEET WILL SAIL AT DAWN AND ATTACK THE SOUTHERN HARBOUR "
            + "WITHOUT WARNING SO WE MUST DEFEND THE COAST AT ONCE";
    private static final String SECOND =
            "THE ENEMY FLEET WILL SAIL AT DUSK AND ATTACK THE NORTHERN HARBOUR "
            + "WITHOUT WARNING SO WE MUST DEFEND THE RIVER AT ONCE";

    private static final int KEY = 12345;
    private static final int ROTOR_COUNT = 3;

    private static final int WIDTH = 5;
    private static final int HEIGHT = 26;
    private static final int TOP = 92;

    /** The last line sits {@link Svg#BOTTOM} above the edge, and nothing is drawn below it. */
    private static final int CANVAS = 206 + Svg.BOTTOM;

    /**
     * Where the square for one position starts, so the row spans the figure's full ink width.
     *
     * <p>The pitch comes out of the width rather than the other way round, which is what keeps the
     * first square on the left edge and the last one on the right however many bytes there are.
     */
    private static int squareX(int index, int count) {
        return Svg.px(Svg.LEFT + index * (double) (Svg.RIGHT - Svg.LEFT - WIDTH) / (count - 1));
    }

    private KeyReuseDiagram() {
    }

    static String render() {
        byte[] first = FIRST.getBytes(StandardCharsets.UTF_8);
        byte[] second = SECOND.getBytes(StandardCharsets.UTF_8);
        if (first.length != second.length) {
            throw new IllegalStateException("the two messages must be the same length to line up");
        }
        int length = first.length;

        ByteEnigma machine = new ByteEnigma(KEY, ROTOR_COUNT);
        byte[] one = machine.transform(first);
        byte[] two = machine.transform(second);

        boolean[] lit = new boolean[length];
        int matches = 0;
        for (int i = 0; i < length; i++) {
            lit[i] = one[i] == two[i];
            if (lit[i] != (first[i] == second[i])) {
                throw new IllegalStateException("ciphertexts agree at " + i + " but plaintexts do not, "
                        + "or the other way round; the figure would be claiming something untrue");
            }
            if (lit[i]) {
                matches++;
            }
        }

        Svg svg = new Svg();
        svg.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"",
                Svg.WIDTH, CANVAS, Svg.WIDTH, CANVAS);
        svg.format("     font-family=\"%s\" role=\"img\"", Svg.FONT);
        svg.line("     aria-labelledby=\"reuse-title reuse-desc\">");
        svg.line("  <title id=\"reuse-title\">What reusing one key gives away</title>");
        svg.format("  <desc id=\"reuse-desc\">Two %d-byte messages enciphered under one key with no nonce. A row of %d",
                length, length);
        svg.format("    squares, one per byte position, has %d of them lit, meaning both ciphertexts carry the same",
                matches);
        svg.format("    byte at that position. The %d unlit squares fall exactly where the two plaintexts differ, so",
                length - matches);
        svg.line("    the pattern of gaps is handed to anyone watching.</desc>");
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"15\" fill=\"%s\">Two messages of %d bytes, enciphered under one key with no nonce.</text>",
                Svg.LEFT, Svg.FIRST_BASELINE, Svg.GREY, length);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\">A lit square means both ciphertexts carry the same byte at that position.</text>",
                Svg.LEFT, Svg.SECOND_BASELINE, Svg.DIM);
        for (int i = 0; i < length; i++) {
            int x = squareX(i, length);
            if (lit[i]) {
                svg.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" fill-opacity=\"0.9\"/>",
                        x, TOP, WIDTH, HEIGHT, Svg.AMBER);
            } else {
                svg.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"none\" stroke=\"%s\" stroke-width=\"1\"/>",
                        x, TOP, WIDTH, HEIGHT, Svg.DIM);
            }
        }
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"160\" font-size=\"16\" fill=\"%s\">%d of %d match, and nobody had to touch the key to see it.</text>",
                Svg.LEFT, Svg.AMBER, matches, length);
        svg.format("  <text x=\"%d\" y=\"184\" font-size=\"13\" fill=\"%s\">The gaps are the whole of what stayed hidden: they are where the messages differ.</text>",
                Svg.LEFT, Svg.DIM);
        svg.format("  <text x=\"%d\" y=\"206\" font-size=\"13\" fill=\"%s\">Seal each message with a fresh nonce and the row goes dark.</text>",
                Svg.LEFT, Svg.GREEN);
        svg.line("</svg>");
        return svg.toString();
    }
}
