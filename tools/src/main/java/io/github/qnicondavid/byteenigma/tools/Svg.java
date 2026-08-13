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
