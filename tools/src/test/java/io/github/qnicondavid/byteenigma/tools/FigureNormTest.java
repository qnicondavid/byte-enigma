package io.github.qnicondavid.byteenigma.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qnicondavid.byteenigma.breaker.QuadgramTableBuilder;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Every figure keeps the same margins, so none of them steps away from the prose around it.
 *
 * <p>Markdown puts an image flush with the text column and scales it to fit, so a margin drawn
 * inside a figure is a margin between that figure and the paragraph above it, and it scales with the
 * window rather than staying put. Six figures once drew six different ones. This is what stops a
 * seventh.
 */
class FigureNormTest {

    /**
     * What an edge defined by a string may be out by.
     *
     * <p>{@link FigureInk} estimates how wide a string prints. Two units is under a third of a
     * character, which is as close as an estimate gets without a font engine.
     */
    private static final double SLACK = 2.0;

    @Test
    void everyFigureIsTheSameWidth() throws IOException {
        for (Map.Entry<String, String> figure : figures().entrySet()) {
            assertEquals(Svg.WIDTH, FigureInk.width(figure.getValue()),
                    figure.getKey() + " is a different width, so its type will print at a different"
                            + " size than the others on the same page");
        }
    }

    @Test
    void everyFigureStartsAndStopsOnTheSameTwoEdges() throws IOException {
        for (Map.Entry<String, String> figure : figures().entrySet()) {
            FigureInk.Bounds ink = FigureInk.of(figure.getValue());
            assertTrue(Math.abs(ink.left() - Svg.LEFT) <= SLACK,
                    figure.getKey() + " starts its ink at " + ink.left() + " rather than " + Svg.LEFT
                            + ", which is a step away from the prose beside it");
            assertTrue(ink.right() <= Svg.RIGHT + SLACK,
                    figure.getKey() + " runs to " + ink.right() + ", past the " + Svg.RIGHT
                            + " every other figure stops at");
        }
    }

    @Test
    void noFigureClipsWhatItDrawsLast() throws IOException {
        for (Map.Entry<String, String> figure : figures().entrySet()) {
            String svg = figure.getValue();
            double below = FigureInk.height(svg) - FigureInk.of(svg).bottom();
            assertEquals(Svg.BOTTOM, below, 0.5,
                    figure.getKey() + " leaves " + below + " units under its last line, where "
                            + Svg.BOTTOM + " is what a descender needs");
        }
    }

    private static Map<String, String> figures() throws IOException {
        return Diagrams.renderAll(QuadgramTableBuilder.projectRoot());
    }
}
