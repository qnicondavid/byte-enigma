package io.github.qnicondavid.byteenigma.tools;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * What a longer message costs each attack.
 *
 * <p>These docs asserted for months that the two attacks separate as the message grows, on the
 * strength of a benchmark that had one message hardcoded into it. The benchmark takes four lengths
 * now and this is what they say: the crib line is flat, because a crib attack decrypts sixteen bytes
 * whatever the message length is, and the ciphertext-only line climbs because it decrypts all of it.
 *
 * <p>The floor underneath both is {@code rekeyOnly}, the key schedule neither of them can skip. The
 * crib line sits just above it at every length, which is the same fact the split diagram shows from
 * the other side.
 */
final class AttackScalingDiagram {

    private static final int[] SIZES = {80, 160, 234, 1024};

    /** Far enough right that the widest number on the vertical axis starts on the left edge. */
    private static final int X0 = 24;

    /** Far enough left that the longest series label ends on the right edge. */
    private static final int X1 = 758;

    private static final int SPAN = 1024;

    /** Where a number on the vertical axis ends, ten units clear of the plot. */
    private static final int AXIS_LABEL = X0 - 10;

    /** Where a series label starts, fourteen units clear of the point it names. */
    private static final int SERIES_LABEL = X1 + 14;

    private static final int BASE = 400;
    private static final int TOP = 100;
    private static final double CEILING = 11.0;

    /** The last line sits {@link Svg#BOTTOM} above the edge, and nothing is drawn below it. */
    private static final int CANVAS = 456 + Svg.BOTTOM;

    private AttackScalingDiagram() {
    }

    static String render(Path results) throws IOException {
        JmhResults jmh = JmhResults.load(results);
        double[] schedule = series(jmh, "rekeyOnly");
        double[] crib = series(jmh, "cribEvaluator");
        double[] language = series(jmh, "languageEvaluator");
        for (int i = 0; i < SIZES.length; i++) {
            if (!(language[i] > crib[i] && crib[i] > schedule[i])) {
                throw new IllegalStateException("at " + SIZES[i] + " bytes the three lines are "
                        + schedule[i] + ", " + crib[i] + " and " + language[i]
                        + ", which do not stack the way this figure draws them");
            }
            if (language[i] > CEILING) {
                throw new IllegalStateException(language[i] + " will not fit under the axis");
            }
        }
        if (!(language[SIZES.length - 1] > language[0])) {
            throw new IllegalStateException("the ciphertext-only line does not climb with the message");
        }

        Svg svg = new Svg();
        svg.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"",
                Svg.WIDTH, CANVAS, Svg.WIDTH, CANVAS);
        svg.format("     font-family=\"%s\" role=\"img\"", Svg.FONT);
        svg.line("     aria-labelledby=\"scale-title scale-desc\">");
        svg.line("  <title id=\"scale-title\">What a longer message costs each attack</title>");
        svg.format("  <desc id=\"scale-desc\">Three lines against message length, in microseconds a candidate. The key");
        svg.format("    schedule is flat at about %.1f. The crib attack is flat just above it, from %.3f microseconds at 80", schedule[0], crib[0]);
        svg.format("    bytes to %.3f at 1024, because it decrypts a sixteen-byte window whatever the message length is.", crib[SIZES.length - 1]);
        svg.format("    The ciphertext-only attack climbs from %.3f to %.3f, because it decrypts all of it, and the shaded", language[0], language[SIZES.length - 1]);
        svg.format("    gap between the two widens from %.1f percent to %.0f percent.</desc>",
                gap(crib[0], language[0]), gap(crib[SIZES.length - 1], language[SIZES.length - 1]));
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"15\" fill=\"%s\">Both attacks pay the key schedule. Only one of them reads the message.</text>", Svg.LEFT, Svg.FIRST_BASELINE, Svg.GREY);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\">Microseconds a candidate, three rotors, against message length in bytes.</text>", Svg.LEFT, Svg.SECOND_BASELINE, Svg.DIM);
        svg.line("");

        for (int level = 0; level <= 10; level += 2) {
            svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\" stroke-opacity=\"0.25\"/>",
                    X0, y(level), X1, y(level), Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"%s\" text-anchor=\"end\">%d</text>",
                    AXIS_LABEL, y(level) + 4, Svg.DIM, level);
        }
        svg.line("");

        StringBuilder band = new StringBuilder();
        for (int i = 0; i < SIZES.length; i++) {
            band.append(String.format(Locale.ROOT, " L %d %d", x(SIZES[i]), y(language[i])));
        }
        for (int i = SIZES.length - 1; i >= 0; i--) {
            band.append(String.format(Locale.ROOT, " L %d %d", x(SIZES[i]), y(crib[i])));
        }
        svg.format("  <path d=\"M %d %d%s Z\" fill=\"%s\" fill-opacity=\"0.14\"/>",
                x(SIZES[0]), y(language[0]), band.substring(band.indexOf(" L", 2)), Svg.AMBER);
        svg.line("");

        polyline(svg, schedule, Svg.DIM, "1.6", "4 4");
        polyline(svg, crib, Svg.GREEN, "2.2", null);
        polyline(svg, language, Svg.AMBER, "2.2", null);
        for (int i = 0; i < SIZES.length; i++) {
            marker(svg, SIZES[i], crib[i], Svg.GREEN);
            marker(svg, SIZES[i], language[i], Svg.AMBER);
        }
        svg.line("");

        svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.5\"/>",
                X0, BASE, X1, BASE, Svg.DIM);
        for (int i = 0; i < SIZES.length; i++) {
            svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\"/>",
                    x(SIZES[i]), BASE, x(SIZES[i]), BASE + 6, Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"%s\" text-anchor=\"%s\">%d</text>",
                    x(SIZES[i]), BASE + 21, Svg.DIM, Svg.tickAnchor(i, SIZES.length), SIZES[i]);
        }
        svg.line("");

        for (int i = 0; i < SIZES.length - 1; i++) {
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"%s\" text-anchor=\"middle\">%.1f%%</text>",
                    x(SIZES[i]), y(language[i]) - 10, Svg.AMBER, gap(crib[i], language[i]));
        }
        int last = SIZES.length - 1;
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\" text-anchor=\"end\">%.0f%% dearer</text>",
                x(SIZES[last]) - 16, Svg.px((y(language[last]) + y(crib[last])) / 2.0), Svg.AMBER,
                gap(crib[last], language[last]));
        svg.line("");

        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">ciphertext only</text>",
                SERIES_LABEL, y(language[last]) + 4, Svg.AMBER);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">with a crib</text>",
                SERIES_LABEL, y(crib[last]) - 6, Svg.GREEN);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">rekey only</text>",
                SERIES_LABEL, y(schedule[last]) + 16, Svg.DIM);
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">Drawn from docs/benchmarks.json. The dashed line is the key schedule, which neither attack can skip.</text>", Svg.LEFT, CANVAS - Svg.BOTTOM, Svg.DIM);
        svg.line("</svg>");
        return svg.toString();
    }

    private static double[] series(JmhResults jmh, String benchmark) {
        double[] values = new double[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            values[i] = jmh.score(benchmark, "messageSize=" + SIZES[i], "rotorCount=3");
        }
        return values;
    }

    private static void polyline(Svg svg, double[] values, String colour, String width, String dashes) {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < SIZES.length; i++) {
            points.append(i == 0 ? "" : " ").append(x(SIZES[i])).append(',').append(y(values[i]));
        }
        svg.format("  <polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"%s\"%s/>",
                points, colour, width, dashes == null ? "" : " stroke-dasharray=\"" + dashes + "\"");
    }

    private static void marker(Svg svg, int size, double value, String colour) {
        svg.format("  <circle cx=\"%d\" cy=\"%d\" r=\"4\" fill=\"%s\"/>", x(size), y(value), colour);
    }

    private static double gap(double crib, double language) {
        return (language - crib) / crib * 100.0;
    }

    private static int x(int size) {
        return Svg.px(X0 + (double) size / SPAN * (X1 - X0));
    }

    private static int y(double microseconds) {
        return Svg.px(BASE - microseconds / CEILING * (BASE - TOP));
    }
}
