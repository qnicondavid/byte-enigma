package io.github.qnicondavid.byteenigma.tools;

import java.util.Locale;

/**
 * A line-at-a-time SVG builder, plus the palette every diagram in this repository uses.
 *
 * <p>The colours are mid-tones on purpose. GitHub renders these pages on a light background and a
 * dark one, and a diagram drawn in black lines vanishes on the dark one.
 *
 * <p>Output has to be identical on every machine, because {@code DiagramReproducibilityTest}
 * compares it byte for byte against what is committed. That is why every number goes through
 * {@link #px} or {@link Locale#ROOT} formatting, and why nothing here reads a clock.
 */
final class Svg {

    /** Body text, and the brightest thing on the page. */
    static final String GREY = "#8b949e";

    /** Axes, ticks, captions, and anything the eye should reach second. */
    static final String DIM = "#7d8590";

    /** The answer: the key, the safe case. */
    static final String GREEN = "#2f9e57";

    /** The finding: a margin worth staring at, or a leak worth worrying about. */
    static final String AMBER = "#bf8700";

    static final String FONT = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace";

    /**
     * How wide every figure is.
     *
     * <p>A page scales a figure to the width of its text column, so two figures of different widths
     * would render their type at different sizes on the same page. Keeping one width keeps one type
     * size across all of them.
     */
    static final int WIDTH = 880;

    /**
     * The left and right edge of the ink.
     *
     * <p>Markdown puts a figure flush with the text column, so whatever margin a figure draws inside
     * itself is a step away from the prose above and below it. Eight units is a hair over half a
     * character: enough that a stroke does not touch the edge, small enough that a reader reads the
     * figure as part of the same column.
     */
    static final int LEFT = 8;

    static final int RIGHT = WIDTH - LEFT;

    /** The baseline of a figure's first line, so the space above one never varies. */
    static final int FIRST_BASELINE = 26;

    /** The second line of a caption, sixteen units under the first. */
    static final int SECOND_BASELINE = FIRST_BASELINE + 22;

    /**
     * How far the last baseline sits above the bottom edge.
     *
     * <p>Enough for the descenders of p, y and g, which one figure used to cut off because its last
     * line sat two units from the bottom.
     */
    static final int BOTTOM = 14;

    /** Where a tick label is anchored, so the first and last on an axis stay inside the ink. */
    static String tickAnchor(int index, int count) {
        if (index == 0) {
            return "start";
        }
        return index == count - 1 ? "end" : "middle";
    }

    private final StringBuilder out = new StringBuilder();

    /** Rounds half up to a whole pixel, which keeps two implementations of a diagram agreeing. */
    static int px(double value) {
        return (int) Math.floor(value + 0.5);
    }

    Svg line(String text) {
        out.append(text).append('\n');
        return this;
    }

    Svg format(String template, Object... arguments) {
        return line(String.format(Locale.ROOT, template, arguments));
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
