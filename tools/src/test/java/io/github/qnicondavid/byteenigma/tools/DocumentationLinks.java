package io.github.qnicondavid.byteenigma.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every local link in the documentation, and whether it lands on anything.
 *
 * <p>A commit once added two figures to {@code docs/benchmarks.md}, pointed the page at them and
 * carried neither, because {@code git add} had refused an unrelated file on the same line and given
 * up on the rest of it. The page rendered two broken images and the build went red on a clean
 * checkout while passing on the machine that made it, because the files were sitting untracked in
 * the working directory. This is the check that would have caught it before the push.
 *
 * <p>It cannot ask git what is tracked, and does not need to: on a fresh clone, present on disk and
 * present in the repository are the same thing, and that is the checkout that matters.
 */
final class DocumentationLinks {

    /** Markdown inline links and images. Reference-style links are not used in this repository. */
    private static final Pattern LINK = Pattern.compile("!?\\[[^\\]]*\\]\\(([^)\\s]+)\\)");

    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,6}\\s+(.*?)\\s*$");

    private DocumentationLinks() {
    }

    /** Every markdown file in the tree, minus build output and scratch, in a stable order. */
    static List<Path> pages(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !skipped(root.relativize(path)))
                    .sorted()
                    .toList();
        }
    }

    /**
     * What is wrong with the links on one page, as sentences, and empty when nothing is.
     *
     * @param root the project root, so a message can name a file the way a reader would
     */
    static List<String> problems(Path root, Path page) throws IOException {
        List<String> problems = new ArrayList<>();
        Matcher links = LINK.matcher(Files.readString(page, StandardCharsets.UTF_8));
        while (links.find()) {
            String target = links.group(1);
            if (external(target)) {
                continue;
            }
            int hash = target.indexOf('#');
            String file = hash < 0 ? target : target.substring(0, hash);
            String anchor = hash < 0 ? "" : target.substring(hash + 1);
            Path destination = file.isEmpty() ? page : page.getParent().resolve(file).normalize();
            String where = root.relativize(page).toString().replace('\\', '/');
            if (!Files.exists(destination)) {
                problems.add(where + " links to " + target + ", and nothing is there");
                continue;
            }
            if (!anchor.isEmpty() && destination.toString().endsWith(".md")
                    && !headings(destination).contains(anchor)) {
                problems.add(where + " links to " + target
                        + ", and that file has no heading by that name");
            }
        }
        return problems;
    }

    /** How many links a page offers up for checking, so a walk that finds nothing can be caught. */
    static int localLinks(Path page) throws IOException {
        int count = 0;
        Matcher links = LINK.matcher(Files.readString(page, StandardCharsets.UTF_8));
        while (links.find()) {
            if (!external(links.group(1))) {
                count++;
            }
        }
        return count;
    }

    private static boolean external(String target) {
        return target.startsWith("http://") || target.startsWith("https://")
                || target.startsWith("mailto:");
    }

    /**
     * The anchors a page offers, by the rule GitHub uses: lower-case the heading, drop everything
     * that is not a letter, a digit, a space or a hyphen, then hyphenate what is left of the spaces.
     */
    private static Set<String> headings(Path page) throws IOException {
        Set<String> anchors = new HashSet<>();
        Matcher headings = HEADING.matcher(Files.readString(page, StandardCharsets.UTF_8));
        while (headings.find()) {
            anchors.add(slug(headings.group(1)));
        }
        return anchors;
    }

    private static String slug(String heading) {
        StringBuilder out = new StringBuilder();
        for (char c : heading.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-') {
                out.append(c);
            } else if (c == ' ' && out.length() > 0 && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    /** Build output, git internals, and the underscore directories this repository keeps for scratch. */
    private static boolean skipped(Path relative) {
        for (Path part : relative.getParent() == null ? relative : relative.getParent()) {
            String name = part.toString();
            if (name.equals("target") || name.startsWith(".") || name.startsWith("_")) {
                return true;
            }
        }
        return false;
    }
}
