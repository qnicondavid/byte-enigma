package io.github.qnicondavid.byteenigma.tools;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Where the time in one candidate goes, and how little of it the message accounts for.
 *
 * <p>Both bars are one key tried against a 234-byte message on three rotors. They share their first
 * three segments, because both attacks build the same machine from the same 32-bit key before they
 * look at anything. What they do afterwards is the short end on the right, and on the crib bar it is
 * short enough to be hard to see, which is the argument these docs make in prose.
 *
 * <p>Every length is read out of {@code docs/benchmarks.json}. The four segments are differences
 * between benchmarks that overlap on purpose, so the arithmetic is checked before anything is drawn:
 * if a difference comes out negative or the parts stop adding up to the whole, this throws rather
 * than drawing a bar that is not the measurement.
 */
final class CandidateSplitDiagram {

    /** Far enough right that the longer of the two row labels starts on the figure's left edge. */
    private static final int X0 = 127;

    private static final int X1 = Svg.RIGHT;

    /** Where a row label ends, ten units clear of the bar it names. */
    private static final int ROW_LABEL = X0 - 10;

    private static final int BAR_TOP = 118;
    private static final int CRIB_TOP = 192;
    private static final int BAR_HEIGHT = 44;

    /** A gap between segments, so four blocks read as four. */
    private static final double SEPARATION = 3.0;

    /** The last line sits {@link Svg#BOTTOM} above the edge, and nothing is drawn below it. */
    private static final int CANVAS = 462 + Svg.BOTTOM;

    private CandidateSplitDiagram() {
    }

    static String render(Path results) throws IOException {
        JmhResults jmh = JmhResults.load(results);
        double base = jmh.score("plainFieldLcg");
        double bounds = jmh.score("plainFieldLcgOverShuffleBounds");
        double schedule = jmh.score("rekeyOnly", "messageSize=234", "rotorCount=3");
        double language = jmh.score("languageEvaluator", "messageSize=234", "rotorCount=3");
        double crib = jmh.score("cribEvaluator", "messageSize=234", "rotorCount=3");

        double loop = bounds - base;
        double rest = schedule - bounds;
        double message = language - schedule;
        double window = crib - schedule;
        check("the rejection loop", loop);
        check("the rest of the shuffling", rest);
        check("the message", message);
        check("the crib window", window);
        double parts = base + loop + rest + message;
        if (Math.abs(parts - language) > 1e-9) {
            throw new IllegalStateException("the segments come to " + parts + " and the candidate to "
                    + language + "; one of them is not what the other is made of");
        }

        Svg svg = new Svg();
        svg.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"",
                Svg.WIDTH, CANVAS, Svg.WIDTH, CANVAS);
        svg.format("     font-family=\"%s\" role=\"img\"", Svg.FONT);
        svg.line("     aria-labelledby=\"split-title split-desc\">");
        svg.line("  <title id=\"split-title\">Where the time in one candidate goes</title>");
        svg.format("  <desc id=\"split-desc\">Two bars on one scale, both for a 234-byte message on three rotors. The");
        svg.format("    ciphertext-only candidate costs %.3f microseconds and the crib one %.3f. Both begin with the same", language, crib);
        svg.format("    %.3f microseconds of key schedule, split into the generator at %.3f, its rejection loop at %.3f", schedule, base, loop);
        svg.format("    and the rest of the shuffling at %.3f. Only the short end differs: %.3f microseconds to read the", rest, message);
        svg.format("    whole message and score it, against %.3f to read the crib window and compare it.</desc>", window);
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"15\" fill=\"%s\">One key tried against a 234-byte message, three rotors.</text>", Svg.LEFT, Svg.FIRST_BASELINE, Svg.GREY);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\">Both attacks build the same machine first. What they do after it is the short end on the right.</text>", Svg.LEFT, Svg.SECOND_BASELINE, Svg.DIM);
        svg.line("");

        int scheduleEnd = x(schedule, language);
        svg.format("  <text x=\"%d\" y=\"80\" font-size=\"13\" fill=\"%s\" text-anchor=\"middle\">the key schedule, %.3f us</text>",
                Svg.px((X0 + scheduleEnd) / 2.0), Svg.GREY, schedule);
        svg.format("  <text x=\"%d\" y=\"96\" font-size=\"12\" fill=\"%s\" text-anchor=\"middle\">%.1f%% of the ciphertext-only bar, %.1f%% of the crib one</text>",
                Svg.px((X0 + scheduleEnd) / 2.0), Svg.DIM, schedule / language * 100.0, schedule / crib * 100.0);
        svg.format("  <path d=\"M %d 110 L %d 102 L %d 102 L %d 110\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.4\"/>",
                X0, X0, scheduleEnd, scheduleEnd, Svg.DIM);
        svg.line("");

        double[] cuts = {0.0, base, base + loop, schedule, language};
        String[] fills = {Svg.DIM, Svg.AMBER, Svg.DIM, Svg.GREEN};
        String[] opacity = {"0.38", "0.92", "0.66", "0.85"};
        for (int i = 0; i < 4; i++) {
            segment(svg, BAR_TOP, cuts[i], cuts[i + 1], language, fills[i], opacity[i]);
        }
        segment(svg, CRIB_TOP, 0.0, base, language, Svg.DIM, "0.38");
        segment(svg, CRIB_TOP, base, base + loop, language, Svg.AMBER, "0.92");
        segment(svg, CRIB_TOP, base + loop, schedule, language, Svg.DIM, "0.66");
        segment(svg, CRIB_TOP, schedule, crib, language, Svg.GREEN, "0.85");

        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\" text-anchor=\"end\">ciphertext only</text>",
                ROW_LABEL, BAR_TOP + 27, Svg.GREY);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\" text-anchor=\"end\">with a crib</text>",
                ROW_LABEL, CRIB_TOP + 27, Svg.GREY);
        for (int i = 0; i < 4; i++) {
            svg.format("  <text x=\"%d\" y=\"180\" font-size=\"12\" fill=\"%s\" text-anchor=\"middle\">%.1f%%</text>",
                    Svg.px((x(cuts[i], language) + x(cuts[i + 1], language)) / 2.0),
                    i == 1 ? Svg.AMBER : Svg.DIM, (cuts[i + 1] - cuts[i]) / language * 100.0);
        }
        svg.line("");

        int axis = CRIB_TOP + BAR_HEIGHT + 18;
        svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.5\"/>",
                X0, axis, X1, axis, Svg.DIM);
        for (int tick = 0; tick <= 5; tick++) {
            svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\"/>",
                    x(tick, language), axis, x(tick, language), axis + 6, Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"%s\" text-anchor=\"%s\">%d</text>",
                    x(tick, language), axis + 21, Svg.DIM, Svg.tickAnchor(tick, 6), tick);
        }
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">microseconds</text>", X0, axis + 42, Svg.DIM);
        svg.line("");

        String[] names = {
            "the generator, 1,275 draws at a power-of-two bound",
            "the rejection loop, on the bounds a shuffle really uses",
            "the rest of the shuffling",
            "the whole message, decrypted and scored",
            "the crib window, decrypted and compared",
        };
        double[] values = {base, loop, rest, message, window};
        double[] wholes = {language, language, language, language, crib};
        String[] swatches = {Svg.DIM, Svg.AMBER, Svg.DIM, Svg.GREEN, Svg.GREEN};
        String[] swatchOpacity = {"0.38", "0.92", "0.66", "0.85", "0.85"};
        for (int i = 0; i < names.length; i++) {
            int y = 330 + i * 22;
            svg.format("  <rect x=\"%d\" y=\"%d\" width=\"12\" height=\"12\" fill=\"%s\" fill-opacity=\"%s\"/>",
                    X0, y - 10, swatches[i], swatchOpacity[i]);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">%s</text>", X0 + 22, y, Svg.DIM, names[i]);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\" text-anchor=\"end\">%.3f us, %.1f%%</text>",
                    Svg.RIGHT, y, Svg.DIM, values[i], values[i] / wholes[i] * 100.0);
        }
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">Drawn from docs/benchmarks.json. Green is the only part that depends on the message.</text>",
                Svg.LEFT, CANVAS - Svg.BOTTOM - 18, Svg.DIM);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\">Each share is of the bar its own segment sits in, so the crib window is measured against the shorter one.</text>",
                Svg.LEFT, CANVAS - Svg.BOTTOM, Svg.DIM);
        svg.line("</svg>");
        return svg.toString();
    }

    private static void segment(Svg svg, int top, double from, double to, double total,
            String fill, String opacity) {
        int left = x(from, total);
        int right = x(to, total);
        double width = right - left - (from > 0.0 ? SEPARATION : 0.0);
        if (width <= 0.0) {
            throw new IllegalStateException("a segment came out " + width + " pixels wide");
        }
        svg.format("  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" fill=\"%s\" fill-opacity=\"%s\"/>",
                Svg.px(left + (from > 0.0 ? SEPARATION : 0.0)), top, Svg.px(width), BAR_HEIGHT, fill, opacity);
    }

    private static int x(double microseconds, double total) {
        return Svg.px(X0 + microseconds / total * (X1 - X0));
    }

    private static void check(String what, double value) {
        if (!(value > 0.0)) {
            throw new IllegalStateException(what + " measures " + value
                    + " microseconds, so the benchmarks it is a difference of no longer nest");
        }
    }
}
