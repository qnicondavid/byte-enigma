package io.github.qnicondavid.byteenigma.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qnicondavid.byteenigma.breaker.QuadgramTableBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Every committed figure has to be what the generator produces from the committed data. */
class DiagramReproducibilityTest {

    @Test
    void everyShippedFigureIsExactlyWhatTheDataProduces() throws IOException {
        Path root = QuadgramTableBuilder.projectRoot();
        for (Map.Entry<String, String> figure : Diagrams.renderAll(root).entrySet()) {
            Path shipped = root.resolve(figure.getKey());
            assertTrue(Files.isRegularFile(shipped), figure.getKey() + " is missing; run Diagrams");
            assertEquals(Files.readString(shipped, StandardCharsets.UTF_8), figure.getValue(),
                    figure.getKey() + " no longer matches what the data produces; run Diagrams and commit both");
        }
    }

    @Test
    void figuresCarryATitleAndADescriptionForScreenReaders() throws IOException {
        for (Map.Entry<String, String> figure : Diagrams.renderAll(QuadgramTableBuilder.projectRoot()).entrySet()) {
            String svg = figure.getValue();
            assertTrue(svg.contains("role=\"img\""), figure.getKey() + " has no img role");
            assertTrue(svg.contains("<title id="), figure.getKey() + " has no title");
            assertTrue(svg.contains("<desc id="), figure.getKey() + " has no description");
        }
    }

    @Test
    void figuresAvoidTheDashesTheProseAvoids() throws IOException {
        for (Map.Entry<String, String> figure : Diagrams.renderAll(QuadgramTableBuilder.projectRoot()).entrySet()) {
            String svg = figure.getValue();
            assertTrue(svg.indexOf('—') < 0, figure.getKey() + " contains an em dash");
            assertTrue(svg.indexOf('–') < 0, figure.getKey() + " contains an en dash");
        }
    }
}
