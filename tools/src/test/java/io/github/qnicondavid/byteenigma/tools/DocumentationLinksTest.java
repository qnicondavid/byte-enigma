package io.github.qnicondavid.byteenigma.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.qnicondavid.byteenigma.breaker.QuadgramTableBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Nothing this repository publishes points at something the repository does not carry. */
class DocumentationLinksTest {

    /**
     * A floor rather than a count, so that adding a page does not fail the build, and so that a walk
     * which quietly finds nothing does.
     */
    private static final int AT_LEAST = 30;

    @Test
    void everyLocalLinkLandsOnSomethingThatExists() throws IOException {
        Path root = QuadgramTableBuilder.projectRoot();
        List<String> problems = new ArrayList<>();
        int links = 0;
        for (Path page : DocumentationLinks.pages(root)) {
            links += DocumentationLinks.localLinks(page);
            problems.addAll(DocumentationLinks.problems(root, page));
        }
        assertEquals(List.of(), problems, "links in the documentation that go nowhere");
        assertTrue(links >= AT_LEAST,
                "only " + links + " local links were found, so the walk covered almost nothing");
    }
}
