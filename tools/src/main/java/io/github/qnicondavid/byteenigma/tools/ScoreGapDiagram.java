package io.github.qnicondavid.byteenigma.tools;

import io.github.qnicondavid.byteenigma.search.Candidate;
import io.github.qnicondavid.byteenigma.search.SweepCheckpoint;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The distance between the key and the best thing that is not the key.
 *
 * <p>Reads the leaderboard out of the committed checkpoint, so the numbers in the picture are the
 * numbers the sweep left behind. Subtracting two rounded scores to get the spread is exactly the
 * mistake this class exists to make impossible: the arithmetic happens at full precision and only
 * the printed labels round.
 */
final class ScoreGapDiagram {

    private static final double LO = -1850.0;
    private static final double HI = -1600.0;
    private static final int X0 = 96;
    private static final int X1 = 800;
    private static final int AXIS = 168;

    private static final double ZLO = -1830.2;
    private static final double ZHI = -1826.4;
    private static final int Z0 = 240;
    private static final int Z1 = 720;
    private static final int ZAXIS = 318;

    private static final int BRACKET = 108;

    private ScoreGapDiagram() {
    }

    private static int sx(double score) {
        return Svg.px(X0 + (score - LO) / (HI - LO) * (X1 - X0));
    }

    private static int zx(double score) {
        return Svg.px(Z0 + (score - ZLO) / (ZHI - ZLO) * (Z1 - Z0));
    }

    static String render(Path checkpoint) throws IOException {
        SweepCheckpoint sweep = SweepCheckpoint.load(checkpoint);
        List<Candidate> board = sweep.best();
        if (board.size() < 10) {
            throw new IllegalStateException(
                    checkpoint + " has " + board.size() + " entries; this diagram needs ten");
        }
        double best = board.get(0).score();
        List<Candidate> rest = board.subList(1, board.size());
        double runnerUp = rest.get(0).score();
        double weakest = rest.get(rest.size() - 1).score();
        double margin = best - runnerUp;
        double spread = runnerUp - weakest;
        int magnification = Svg.px(((double) (Z1 - Z0) / (ZHI - ZLO)) / ((double) (X1 - X0) / (HI - LO)));

        int loX = sx(runnerUp);
        int hiX = sx(best);

        Svg svg = new Svg();
        svg.line("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 880 372\" width=\"880\" height=\"372\"");
        svg.format("     font-family=\"%s\" role=\"img\"", Svg.FONT);
        svg.line("     aria-labelledby=\"gap-title gap-desc\">");
        svg.line("  <title id=\"gap-title\">The margin between the key and every other key</title>");
        svg.line("  <desc id=\"gap-desc\">A score axis from minus 1850 to minus 1600. The nine next best keys out of");
        svg.format("    4,294,967,296 sit in one clump near minus 1828 and span %.2f log-units between them. The true", spread);
        svg.format("    key sits far to the right at %.2f, a margin of %.2f. A second axis below magnifies the", best, margin);
        svg.line("    clump so the nine can be told apart.</desc>");
        svg.line("");
        svg.format("  <text x=\"96\" y=\"40\" font-size=\"15\" fill=\"%s\">%,d keys tried, from the ciphertext alone.</text>",
                Svg.GREY, sweep.keysTried());
        svg.format("  <text x=\"96\" y=\"62\" font-size=\"13\" fill=\"%s\">Further right means the bytes that came out read more like English.</text>",
                Svg.DIM);
        svg.line("");
        svg.format("  <path d=\"M %d %d L %d %d L %d %d L %d %d\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.5\"/>",
                loX, AXIS - 16, loX, BRACKET, hiX, BRACKET, hiX, AXIS - 16, Svg.AMBER);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"16\" fill=\"%s\" text-anchor=\"middle\">a margin of %.2f log-units</text>",
                Svg.px((loX + hiX) / 2.0), BRACKET - 12, Svg.AMBER, margin);
        svg.line("");
        svg.format("  <line x1=\"96\" y1=\"%d\" x2=\"808\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.5\"/>", AXIS, AXIS, Svg.DIM);
        for (int tick = -1850; tick <= -1600; tick += 50) {
            int x = sx(tick);
            svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\"/>",
                    x, AXIS, x, AXIS + 7, Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"%s\" text-anchor=\"middle\">%d</text>",
                    x, AXIS + 24, Svg.DIM, tick);
        }
        for (Candidate candidate : rest) {
            svg.format("  <circle cx=\"%d\" cy=\"%d\" r=\"6\" fill=\"%s\" fill-opacity=\"0.55\"/>",
                    sx(candidate.score()), AXIS, Svg.DIM);
        }
        svg.format("  <circle cx=\"%d\" cy=\"%d\" r=\"9\" fill=\"%s\"/>", hiX, AXIS, Svg.GREEN);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\" text-anchor=\"middle\">nine keys</text>",
                loX, AXIS + 48, Svg.DIM);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"13\" fill=\"%s\" text-anchor=\"middle\">the key</text>",
                hiX, AXIS + 48, Svg.GREEN);
        svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\" text-anchor=\"middle\">%.2f</text>",
                hiX, AXIS + 64, Svg.GREEN, best);
        svg.line("");
        svg.format("  <text x=\"240\" y=\"%d\" font-size=\"13\" fill=\"%s\">the same nine keys, magnified %d times</text>",
                ZAXIS - 42, Svg.DIM, magnification);
        svg.format("  <line x1=\"240\" y1=\"%d\" x2=\"720\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1.5\"/>",
                ZAXIS, ZAXIS, Svg.DIM);
        for (int tick = -1830; tick <= -1827; tick++) {
            int x = zx(tick);
            svg.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\" stroke-width=\"1\"/>",
                    x, ZAXIS, x, ZAXIS + 7, Svg.DIM);
            svg.format("  <text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"%s\" text-anchor=\"middle\">%d</text>",
                    x, ZAXIS + 24, Svg.DIM, tick);
        }
        for (Candidate candidate : rest) {
            svg.format("  <circle cx=\"%d\" cy=\"%d\" r=\"6\" fill=\"%s\" fill-opacity=\"0.55\"/>",
                    zx(candidate.score()), ZAXIS, Svg.DIM);
        }
        svg.format("  <text x=\"96\" y=\"%d\" font-size=\"12\" fill=\"%s\">%.2f log-units</text>", ZAXIS - 12, Svg.DIM, spread);
        svg.format("  <text x=\"96\" y=\"%d\" font-size=\"12\" fill=\"%s\">from end to end,</text>", ZAXIS + 4, Svg.DIM);
        svg.format("  <text x=\"96\" y=\"%d\" font-size=\"12\" fill=\"%s\">and no English</text>", ZAXIS + 20, Svg.DIM);
        svg.format("  <text x=\"96\" y=\"%d\" font-size=\"12\" fill=\"%s\">in any of them</text>", ZAXIS + 36, Svg.DIM);
        svg.line("</svg>");
        return svg.toString();
    }
}
