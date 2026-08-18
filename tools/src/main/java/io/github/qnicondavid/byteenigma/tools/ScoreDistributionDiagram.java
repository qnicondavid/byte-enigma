package io.github.qnicondavid.byteenigma.tools;

import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SweepCheckpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What every key in the space scored, on a log scale, twice.
 *
 * <p>The leaderboard says the margin is 206 log-units. This says what 206 is large compared to,
 * which is the question a reader actually has. The top panel puts the whole range on one axis and
 * the gap takes up most of it. The bottom panel magnifies the pile on the left, where almost every
 * key in the space lands on one score and the twenty-four bins above it hold nothing.
 *
 * <p>Bin counts come from the histogram the sweep wrote. The margin comes from the checkpoint at
 * full precision, because subtracting two bin edges would be out by a hundredth and this repository
 * has already published that mistake once.
 */
final class ScoreDistributionDiagram {

    /** Far enough right that the widest power-of-ten label starts on the figure's left edge. */
    private static final int AX0 = 38;

    private static final int AX1 = Svg.RIGHT;

    /** Where a power-of-ten label ends, eight units clear of the grid. */
    private static final int AXIS_LABEL = AX0 - 8;

    /** The last line sits {@link Svg#BOTTOM} above the edge, and nothing is drawn below it. */
    private static final int CANVAS = 602 + Svg.BOTTOM;

    private static final double TOP_LO = -1862.0;
    private static final double TOP_HI = -1610.0;
    private static final int TOP_BASE = 300;
    private static final int TOP_HEIGHT = 190;

    private static final double ZOOM_LO = -1852.6;
    private static final double ZOOM_HI = -1826.0;
    private static final int ZOOM_BASE = 512;
    private static final int ZOOM_HEIGHT = 140;

    /** Decades on the vertical axis, from one key to ten billion. */
    private static final double DECADES = 10.0;

    private record Bin(double edge, long count) {
    }

    private ScoreDistributionDiagram() {
    }

    private static int ax(double score) {
        return Svg.px(AX0 + (score - TOP_LO) / (TOP_HI - TOP_LO) * (AX1 - AX0));
    }

    private static int bx(double score) {
        return Svg.px(AX0 + (score - ZOOM_LO) / (ZOOM_HI - ZOOM_LO) * (AX1 - AX0));
    }

    private static int ay(double count) {
        return Svg.px(TOP_BASE - Math.log10(count) / DECADES * TOP_HEIGHT);
    }

    private static int by(double count) {
        return Svg.px(ZOOM_BASE - Math.log10(count) / DECADES * ZOOM_HEIGHT);
    }

    private static List<Bin> readBins(Path histogram) throws IOException {
        List<Bin> bins = new ArrayList<>();
        for (String line : Files.readAllLines(histogram, StandardCharsets.UTF_8)) {
            if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                String[] fields = line.split("\t");
                bins.add(new Bin(Double.parseDouble(fields[1]), Long.parseLong(fields[2])));
            }
        }
        if (bins.size() < 2) {
            throw new IllegalStateException(histogram + " holds " + bins.size() + " populated bins");
        }
        return bins;
    }

    static String render(Path histogram, Path checkpoint) throws IOException {
        List<Bin> bins = readBins(histogram);
        List<Candidate> board = SweepCheckpoint.load(checkpoint).best();
        double margin = board.get(0).score() - board.get(1).score();

        long total = 0L;
        for (Bin bin : bins) {
            total += bin.count();
        }
        Bin key = bins.get(bins.size() - 1);
        List<Bin> noise = bins.subList(0, bins.size() - 1);
        Bin floor = noise.get(0);
        double topNoise = noise.get(noise.size() - 1).edge();
        for (Bin bin : noise) {
            topNoise = Math.max(topNoise, bin.edge());
        }

        Svg svg = new Svg();
        svg.format("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\"",
                Svg.WIDTH, CANVAS, Svg.WIDTH, CANVAS);
        svg.format("     font-family=\"%s\" role=\"img\"", Svg.FONT);
        svg.line("     aria-labelledby=\"hist-title hist-desc\">");
        svg.line("  <title id=\"hist-title\">What all 4,294,967,296 keys scored</title>");
        svg.line("  <desc id=\"hist-desc\">Two log-scale histograms of every score in the sweep. In the first, the");
        svg.line("    noise piles up on the left between minus 1851.8 and minus 1826.8, then 206 log-units pass with");
        svg.line("    nothing in them at all, then the key stands alone on the right. The second magnifies the noise:");
        svg.line("    4,249,795,476 keys, 98.95 percent of the space, share one bin at the floor, twenty-four bins");
        svg.line("    above it are empty, and the rest thins from millions a bin down to one.</desc>");
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"15\" fill=\"%s\">Every key in the space, counted into bins of 0.1 log-units.</text>", Svg.LEFT, Svg.FIRST_BASELINE, Svg.GREY);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\">Height is how many keys scored that much. The scale is logarithmic: each line up is a hundredfold.</text>", Svg.LEFT, Svg.SECOND_BASELINE, Svg.DIM);
        svg.line("");
        grid(svg, TOP_BASE, true);
        for (Bin bin : noise) {
            svg.format("  <line x1=\"%d\" y1=\"300\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.6\" stroke-opacity=\"0.75\"/>",
                    ax(bin.edge()), ax(bin.edge()), ay(bin.count()), Svg.DIM);
        }
        svg.format("  <line x1=\"%d\" y1=\"300\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"3\"/>",
                ax(key.edge()), ax(key.edge()), ay(key.count()) - 6, Svg.GREEN);
        svg.format("  <circle cx=\"%d\" cy=\"%d\" r=\"6\" fill=\"%s\"/>", ax(key.edge()), 300, Svg.GREEN);
        svg.format("  <line x1=\"%d\" y1=\"300\" x2=\"%d\" y2=\"300\" stroke=\"%s\" stroke-width=\"1.5\"/>", AX0, AX1, Svg.DIM);
        int ticks = 1 + (-1610 - -1850) / 40;
        for (int i = 0; i < ticks; i++) {
            int tick = -1850 + i * 40;
            svg.format("  <line x1=\"%d\" y1=\"300\" x2=\"%d\" y2=\"307\" stroke=\"%s\" stroke-width=\"1\"/>",
                    ax(tick), ax(tick), Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"322\" font-size=\"11\" fill=\"%s\" text-anchor=\"%s\">%d</text>",
                    ax(tick), Svg.DIM, Svg.tickAnchor(i, ticks), tick);
        }
        int gapLeft = ax(topNoise);
        int gapRight = ax(key.edge());
        svg.format("  <path d=\"M %d 108 L %d 92 L %d 92 L %d 108\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.5\"/>",
                gapLeft, gapLeft, gapRight, gapRight, Svg.AMBER);
        svg.format("  <text x=\"%d\" y=\"84\" font-size=\"15\" fill=\"%s\" text-anchor=\"middle\">%.2f log-units, and not one key in them</text>",
                Svg.px((gapLeft + gapRight) / 2.0), Svg.AMBER, margin);
        svg.format("  <text x=\"%d\" y=\"284\" font-size=\"12\" fill=\"%s\" text-anchor=\"middle\">the key</text>",
                gapRight, Svg.GREEN);
        svg.line("");
        svg.format("  <text x=\"%d\" y=\"356\" font-size=\"14\" fill=\"%s\">The left-hand pile, magnified.</text>", Svg.LEFT, Svg.GREY);
        grid(svg, ZOOM_BASE, false);
        for (Bin bin : noise) {
            svg.format("  <line x1=\"%d\" y1=\"512\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"2.4\" stroke-opacity=\"0.85\"/>",
                    bx(bin.edge()), bx(bin.edge()), by(bin.count()),
                    bin.edge() == floor.edge() ? Svg.AMBER : Svg.DIM);
        }
        svg.format("  <line x1=\"%d\" y1=\"512\" x2=\"%d\" y2=\"512\" stroke=\"%s\" stroke-width=\"1.5\"/>", AX0, AX1, Svg.DIM);
        int zooms = 1 + (-1827 - -1852) / 5;
        for (int i = 0; i < zooms; i++) {
            int tick = -1852 + i * 5;
            svg.format("  <line x1=\"%d\" y1=\"512\" x2=\"%d\" y2=\"519\" stroke=\"%s\" stroke-width=\"1\"/>",
                    bx(tick), bx(tick), Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"534\" font-size=\"11\" fill=\"%s\" text-anchor=\"%s\">%d</text>",
                    bx(tick), Svg.DIM, Svg.tickAnchor(i, zooms), tick);
        }
        svg.format("  <text x=\"%d\" y=\"396\" font-size=\"12\" fill=\"%s\">%,d keys, %.2f%% of the space,</text>",
                bx(floor.edge()) + 16, Svg.AMBER, floor.count(), 100.0 * floor.count() / total);
        svg.format("  <text x=\"%d\" y=\"412\" font-size=\"12\" fill=\"%s\">all landing on exactly the same score</text>",
                bx(floor.edge()) + 16, Svg.AMBER);
        svg.format("  <text x=\"%d\" y=\"566\" font-size=\"12\" fill=\"%s\">A candidate whose decryption holds no run of four letters is charged the floor on every window, so it</text>", Svg.LEFT, Svg.DIM);
        svg.format("  <text x=\"%d\" y=\"584\" font-size=\"12\" fill=\"%s\">lands on the same number as all the others. The twenty-four bins directly above it are empty, because</text>", Svg.LEFT, Svg.DIM);
        svg.format("  <text x=\"%d\" y=\"602\" font-size=\"12\" fill=\"%s\">recognising a single quadgram is worth about 2.5 log-units and there is no smaller step to take.</text>", Svg.LEFT, Svg.DIM);
        svg.line("</svg>");
        return svg.toString();
    }

    private static void grid(Svg svg, int base, boolean top) {
        for (int power = 0; power <= 10; power += 2) {
            int y = power == 0 ? base : (top ? ay(Math.pow(10, power)) : by(Math.pow(10, power)));
            svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\" stroke-opacity=\"0.25\"/>",
                    AX0, y, AX1, y, Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"10\" fill=\"%s\" text-anchor=\"end\">%s</text>",
                    AXIS_LABEL, y + 4, Svg.DIM, power == 0 ? "1" : "10^" + power);
        }
    }
}
