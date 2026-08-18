package io.github.qnicondavid.byteenigma.tools;

/**
 * The path one byte takes through the machine, out to the reflector and back.
 *
 * <p>This is the only figure in the repository with no measurement behind it. It draws the shape of
 * the cipher rather than a result, which is why it takes no arguments. It is generated all the same,
 * because a hand-written SVG is a place where the margins drift and where a label can quietly outgrow
 * the box it sits in. Both of those had happened to it.
 *
 * <p>Every position here comes out of the widths: the boxes are sized to the longest label they
 * carry, the chain is spaced so that it starts and ends on the figure's ink edges, and the rows are
 * spaced from one another. Nothing is a number that looked right once.
 */
final class SignalPathDiagram {

    private static final String FORWARD = "#3b82c4";
    private static final String BACKWARD = "#8b5cd6";

    /** Where the first ink sits, level with where a first line of text would print. */
    private static final int TOP = Svg.FIRST_BASELINE - 10;

    private static final int ROW_HEIGHT = 56;

    /** The vertical gap between the forward row and the one coming back. */
    private static final int ROW_GAP = 28;

    private static final int BACK_TOP = TOP + ROW_HEIGHT + ROW_GAP;
    private static final int REFLECTOR_HEIGHT = BACK_TOP + ROW_HEIGHT - TOP;

    /** Where the chain begins, leaving room for the word in front of it and its arrow. */
    private static final int CHAIN = 96;

    private static final int PLUGBOARD_WIDTH = 96;

    /**
     * Wide enough for {@code steps every byte}, the longest thing any rotor says.
     *
     * <p>At font size 11 a monospace character is about 6.6 units, so sixteen of them come to 106.
     * The box used to be 88 and the label hung nine units out of each side of it.
     */
    private static final int ROTOR_WIDTH = 124;

    private static final int REFLECTOR_WIDTH = 120;

    /**
     * How far a stroked box sits inside the edge it lines up with.
     *
     * <p>A stroke straddles the line it is drawn on, so a box whose border sits exactly on the edge
     * of the figure loses the outer half of that border to the edge. Two units puts the whole of a
     * two and a half unit border inside, at a cost of under a quarter of a percent of the width.
     */
    private static final int BORDER_INSET = 2;

    private static final int RULE = BACK_TOP + ROW_HEIGHT + 44;

    private static final int CAPTION = RULE + 30;

    private static final int HEIGHT = CAPTION + 162 + Svg.BOTTOM;

    private SignalPathDiagram() {
    }

    /** The left edge of each of the five elements, spaced so the last one ends on the ink edge. */
    private static int[] columns() {
        int[] widths = {PLUGBOARD_WIDTH, ROTOR_WIDTH, ROTOR_WIDTH, ROTOR_WIDTH, REFLECTOR_WIDTH};
        int total = 0;
        for (int width : widths) {
            total += width;
        }
        int gap = (Svg.RIGHT - CHAIN - total) / (widths.length - 1);
        int[] left = new int[widths.length];
        int x = CHAIN;
        for (int i = 0; i < widths.length; i++) {
            left[i] = x;
            x += widths[i] + gap;
        }
        left[widths.length - 1] = Svg.RIGHT - REFLECTOR_WIDTH - BORDER_INSET;
        return left;
    }

    static String render() {
        int[] left = columns();
        int[] width = {PLUGBOARD_WIDTH, ROTOR_WIDTH, ROTOR_WIDTH, ROTOR_WIDTH, REFLECTOR_WIDTH};
        int forwardMid = TOP + ROW_HEIGHT / 2;
        int backMid = BACK_TOP + ROW_HEIGHT / 2;

        Svg svg = new Svg();
        svg.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"",
                Svg.WIDTH, HEIGHT, Svg.WIDTH, HEIGHT);
        svg.format("     font-family=\"%s\" role=\"img\"", Svg.FONT);
        svg.line("     aria-labelledby=\"path-title path-desc\">");
        svg.line("  <title id=\"path-title\">The signal path of byte-enigma</title>");
        svg.line("  <desc id=\"path-desc\">A byte enters the plugboard, passes forward through three rotors, meets the");
        svg.line("    reflector, returns backward through the same three rotors in reverse order, passes through the");
        svg.line("    plugboard again, and leaves. The reflector is a fixed-point-free involution, which is why the");
        svg.line("    machine is reciprocal and why no byte can ever come out as itself.</desc>");
        svg.line("");
        svg.line("  <defs>");
        marker(svg, "arrow-fwd", FORWARD);
        marker(svg, "arrow-bwd", BACKWARD);
        svg.line("  </defs>");
        svg.line("");

        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"13\">in</text>",
                Svg.LEFT, forwardMid + 6, Svg.DIM);
        arrow(svg, Svg.LEFT + 32, left[0], forwardMid, FORWARD, "arrow-fwd");
        box(svg, left[0], TOP, PLUGBOARD_WIDTH, ROW_HEIGHT, Svg.GREEN, "2");
        label(svg, left[0] + PLUGBOARD_WIDTH / 2, TOP + 25, 14, Svg.DIM, "plugboard", true);
        label(svg, left[0] + PLUGBOARD_WIDTH / 2, TOP + 43, 12, Svg.GREY, "128 pairs", false);

        String[] under = {"steps every byte", "on turnover", "on turnover"};
        for (int i = 0; i < 3; i++) {
            arrow(svg, left[i] + width[i], left[i + 1], forwardMid, FORWARD, "arrow-fwd");
            box(svg, left[i + 1], TOP, ROTOR_WIDTH, ROW_HEIGHT, FORWARD, "2");
            label(svg, left[i + 1] + ROTOR_WIDTH / 2, TOP + 25, 14, Svg.DIM, "rotor " + i, true);
            label(svg, left[i + 1] + ROTOR_WIDTH / 2, TOP + 43, 11, Svg.GREY, under[i], false);
        }
        arrow(svg, left[3] + ROTOR_WIDTH, left[4], forwardMid, FORWARD, "arrow-fwd");
        svg.line("");

        box(svg, left[4], TOP, REFLECTOR_WIDTH, REFLECTOR_HEIGHT, Svg.AMBER, "2.5");
        int reflectorMid = left[4] + REFLECTOR_WIDTH / 2;
        label(svg, reflectorMid, TOP + REFLECTOR_HEIGHT / 2 - 12, 14, Svg.DIM, "reflector", true);
        label(svg, reflectorMid, TOP + REFLECTOR_HEIGHT / 2 + 9, 12, Svg.GREY, "128 pairs,", false);
        label(svg, reflectorMid, TOP + REFLECTOR_HEIGHT / 2 + 26, 12, Svg.GREY, "no fixed point", false);
        svg.line("");

        for (int i = 3; i >= 1; i--) {
            arrow(svg, left[i + 1], left[i] + ROTOR_WIDTH, backMid, BACKWARD, "arrow-bwd");
            box(svg, left[i], BACK_TOP, ROTOR_WIDTH, ROW_HEIGHT, BACKWARD, "2");
            label(svg, left[i] + ROTOR_WIDTH / 2, BACK_TOP + 25, 14, Svg.DIM, "rotor " + (i - 1), true);
            label(svg, left[i] + ROTOR_WIDTH / 2, BACK_TOP + 43, 11, Svg.GREY, "inverse", false);
        }
        arrow(svg, left[1], left[0] + PLUGBOARD_WIDTH, backMid, BACKWARD, "arrow-bwd");
        box(svg, left[0], BACK_TOP, PLUGBOARD_WIDTH, ROW_HEIGHT, Svg.GREEN, "2");
        label(svg, left[0] + PLUGBOARD_WIDTH / 2, BACK_TOP + 32, 14, Svg.DIM, "plugboard", true);
        arrow(svg, left[0], Svg.LEFT + 36, backMid, BACKWARD, "arrow-bwd");
        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"13\">out</text>",
                Svg.LEFT, backMid + 6, Svg.DIM);
        svg.line("");

        svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\" opacity=\"0.4\"/>",
                Svg.LEFT, RULE, Svg.RIGHT, RULE, Svg.GREY);
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"12.5\" font-style=\"italic\">Everything above the line is derived from one 32-bit key.</text>",
                Svg.LEFT, CAPTION, Svg.GREY);
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"12.5\">", Svg.LEFT, CAPTION + 30, Svg.GREY);
        svg.format("    <tspan fill=\"%s\" font-weight=\"600\">plugboard</tspan><tspan> and </tspan><tspan fill=\"%s\" font-weight=\"600\">reflector</tspan><tspan>: fixed-point-free involutions of the 256 byte values, so</tspan>",
                Svg.GREEN, Svg.AMBER);
        svg.line("  </text>");
        caption(svg, CAPTION + 48, "applying either one twice is the identity.");
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"12.5\">", Svg.LEFT, CAPTION + 76, Svg.GREY);
        svg.format("    <tspan fill=\"%s\" font-weight=\"600\">rotors</tspan><tspan> forward and </tspan><tspan fill=\"%s\" font-weight=\"600\">rotors</tspan><tspan> back: the same key-derived permutations, applied in</tspan>",
                FORWARD, BACKWARD);
        svg.line("  </text>");
        caption(svg, CAPTION + 94, "reverse order and inverted, with an offset that advances as the message is consumed.");
        svg.line("");
        caption(svg, CAPTION + 126, "The reflector has no fixed point and every rotor pass is a conjugation of it, so the whole machine");
        caption(svg, CAPTION + 144, "inherits the property: no byte ever comes out as the byte that went in. That is what makes one");
        caption(svg, CAPTION + 162, "setting both encrypt and decrypt, and what lets an attacker rule out crib positions for free.");
        svg.line("</svg>");
        return svg.toString();
    }

    private static void marker(Svg svg, String id, String colour) {
        svg.format("    <marker id=\"%s\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\"", id);
        svg.line("            markerWidth=\"6\" markerHeight=\"6\" orient=\"auto-start-reverse\">");
        svg.format("      <path d=\"M 0 0 L 10 5 L 0 10 z\" fill=\"%s\"/>", colour);
        svg.line("    </marker>");
    }

    private static void box(Svg svg, int x, int y, int w, int h, String colour, String stroke) {
        svg.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"6\" fill=\"none\" stroke=\"%s\" stroke-width=\"%s\"/>",
                x, y, w, h, colour, stroke);
    }

    private static void label(Svg svg, int x, int y, int size, String colour, String text, boolean bold) {
        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"%d\"%s text-anchor=\"middle\">%s</text>",
                x, y, colour, size, bold ? " font-weight=\"600\"" : "", text);
    }

    private static void caption(Svg svg, int y, String text) {
        svg.format("  <text x=\"%d\" y=\"%d\" fill=\"%s\" font-size=\"12.5\">%s</text>", Svg.LEFT, y, Svg.GREY, text);
    }

    private static void arrow(Svg svg, int from, int to, int y, String colour, String marker) {
        svg.format("  <path d=\"M %d %d L %d %d\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\" marker-end=\"url(#%s)\"/>",
                from, y, to, y, colour, marker);
    }
}
