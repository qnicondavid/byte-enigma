package io.github.qnicondavid.byteenigma.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The box a figure's ink actually occupies, so the margins can be checked rather than eyeballed.
 *
 * <p>Six figures used to draw six different margins inside themselves. Markdown puts an image flush
 * with the text column, so each of those margins was a step away from the prose above and below it,
 * and the left ones ranged from 16 units to 96 while the right ones ranged from 5.7% of the width to
 * 13.6%. Nothing in the build could see it.
 *
 * <p>Geometry is measured exactly. Text is estimated: every figure sets one monospace family, where
 * a character advances about six tenths of the font size, and this uses that. The estimate is a
 * little generous for most of the fonts in that stack, which is the safe direction to be wrong in,
 * and it is why the check that uses this allows a couple of units of slack on an edge a string
 * defines.
 */
final class FigureInk {

    /** What one character advances, as a fraction of the font size, in a monospace face. */
    private static final double ADVANCE = 0.6;

    private static final Pattern DEFS = Pattern.compile("(?s)<defs>.*?</defs>");
    private static final Pattern TEXT = Pattern.compile("(?s)<text([^>]*)>(.*?)</text>");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern RECT = Pattern.compile("<rect([^>]*)>");
    private static final Pattern LINE = Pattern.compile("<line([^>]*)>");
    private static final Pattern CIRCLE = Pattern.compile("<circle([^>]*)>");
    private static final Pattern PATH = Pattern.compile("<path[^>]*\\sd=\"([^\"]+)\"");
    private static final Pattern POLYLINE = Pattern.compile("<polyline[^>]*\\spoints=\"([^\"]+)\"");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** Where the ink starts and stops, in the figure's own units. */
    record Bounds(double left, double right, double top, double bottom) {
    }

    private FigureInk() {
    }

    /** The height the figure declares, which the bottom margin is measured against. */
    static int height(String svg) {
        Matcher box = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        if (!box.find()) {
            throw new IllegalStateException("a figure has no viewBox starting at the origin");
        }
        return Integer.parseInt(box.group(2));
    }

    /** The width the figure declares, which every figure shares so type comes out one size. */
    static int width(String svg) {
        Matcher box = Pattern.compile("viewBox=\"0 0 (\\d+) (\\d+)\"").matcher(svg);
        if (!box.find()) {
            throw new IllegalStateException("a figure has no viewBox starting at the origin");
        }
        return Integer.parseInt(box.group(1));
    }

    /** Everything drawn, ignoring marker definitions, which live in their own coordinate space. */
    static Bounds of(String svg) {
        String drawn = DEFS.matcher(svg).replaceAll("");
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();

        Matcher text = TEXT.matcher(drawn);
        while (text.find()) {
            String attributes = text.group(1);
            double x = attribute(attributes, "x");
            double size = has(attributes, "font-size") ? attribute(attributes, "font-size") : 12.0;
            double width = TAGS.matcher(text.group(2)).replaceAll("").trim().length() * size * ADVANCE;
            String anchor = anchor(attributes);
            double start = switch (anchor) {
                case "middle" -> x - width / 2.0;
                case "end" -> x - width;
                default -> x;
            };
            xs.add(start);
            xs.add(start + width);
            ys.add(attribute(attributes, "y"));
        }

        Matcher rect = RECT.matcher(drawn);
        while (rect.find()) {
            double x = attribute(rect.group(1), "x");
            double y = attribute(rect.group(1), "y");
            xs.add(x);
            xs.add(x + attribute(rect.group(1), "width"));
            ys.add(y);
            ys.add(y + attribute(rect.group(1), "height"));
        }

        Matcher line = LINE.matcher(drawn);
        while (line.find()) {
            xs.add(attribute(line.group(1), "x1"));
            xs.add(attribute(line.group(1), "x2"));
            ys.add(attribute(line.group(1), "y1"));
            ys.add(attribute(line.group(1), "y2"));
        }

        Matcher circle = CIRCLE.matcher(drawn);
        while (circle.find()) {
            double radius = attribute(circle.group(1), "r");
            xs.add(attribute(circle.group(1), "cx") - radius);
            xs.add(attribute(circle.group(1), "cx") + radius);
            ys.add(attribute(circle.group(1), "cy") - radius);
            ys.add(attribute(circle.group(1), "cy") + radius);
        }

        points(PATH.matcher(drawn), xs, ys);
        points(POLYLINE.matcher(drawn), xs, ys);

        if (xs.isEmpty()) {
            throw new IllegalStateException("a figure draws nothing at all");
        }
        return new Bounds(min(xs), max(xs), min(ys), max(ys));
    }

    private static void points(Matcher matcher, List<Double> xs, List<Double> ys) {
        while (matcher.find()) {
            Matcher number = NUMBER.matcher(matcher.group(1));
            boolean horizontal = true;
            while (number.find()) {
                (horizontal ? xs : ys).add(Double.parseDouble(number.group()));
                horizontal = !horizontal;
            }
        }
    }

    private static boolean has(String attributes, String name) {
        return Pattern.compile("\\s" + name + "=\"").matcher(attributes).find();
    }

    private static String anchor(String attributes) {
        Matcher matcher = Pattern.compile("text-anchor=\"(\\w+)\"").matcher(attributes);
        return matcher.find() ? matcher.group(1) : "start";
    }

    private static double attribute(String attributes, String name) {
        Matcher matcher = Pattern.compile("\\s" + name + "=\"(-?[\\d.]+)\"").matcher(attributes);
        if (!matcher.find()) {
            throw new IllegalStateException("no " + name + " in" + attributes);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static double min(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
    }

    private static double max(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
    }
}
