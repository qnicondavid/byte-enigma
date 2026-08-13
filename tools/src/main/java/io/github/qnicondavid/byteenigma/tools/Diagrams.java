package io.github.qnicondavid.byteenigma.tools;

import io.github.qnicondavid.byteenigma.breaker.QuadgramTableBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes every generated figure in {@code docs/}.
 *
 * <p>Run it after changing anything a figure draws from, then commit the SVGs beside the data.
 * {@code DiagramReproducibilityTest} fails the build if the two ever disagree, which is the same
 * arrangement the quadgram table lives under and for the same reason.
 */
public final class Diagrams {

    private Diagrams() {
    }

    /** Every figure this module owns, by the path it is written to. */
    public static Map<String, String> renderAll(Path root) throws IOException {
        Map<String, String> figures = new LinkedHashMap<>();
        figures.put("docs/score-gap.svg", ScoreGapDiagram.render(root.resolve("docs/keyspace-sweep.state")));
        figures.put("docs/key-reuse.svg", KeyReuseDiagram.render());
        return figures;
    }

    public static void main(String[] args) throws IOException {
        Path root = QuadgramTableBuilder.projectRoot();
        for (Map.Entry<String, String> figure : renderAll(root).entrySet()) {
            Path target = root.resolve(figure.getKey());
            Files.writeString(target, figure.getValue(), StandardCharsets.UTF_8);
            System.out.println("wrote " + target);
        }
    }
}
