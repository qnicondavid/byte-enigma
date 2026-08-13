package io.github.qnicondavid.byteenigma.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The distribution has to add up, and has to say so when its range was chosen badly. */
class ScoreHistogramTest {

    private static ScoreHistogram tenWideBins() {
        return new ScoreHistogram(-100.0, 0.0, 10.0);
    }

    @Test
    void everyScoreLandsInExactlyOneBinOrOneOverflow() {
        ScoreHistogram histogram = tenWideBins();
        for (double score : new double[] {-95.0, -95.5, -0.5, -100.0, -100.1, 0.0, 12.0}) {
            histogram.record(score);
        }
        long inBins = 0L;
        for (long count : histogram.bins()) {
            inBins += count;
        }
        assertEquals(7L, histogram.counted());
        assertEquals(7L, inBins + histogram.under() + histogram.over());
        assertEquals(1L, histogram.under(), "-100.1 is below the first bin");
        assertEquals(2L, histogram.over(), "0.0 and 12.0 are at or above the last edge");
    }

    @Test
    void theLowEdgeBelongsToItsBinAndTheHighEdgeDoesNot() {
        ScoreHistogram histogram = tenWideBins();
        histogram.record(-100.0);
        histogram.record(-90.0);
        assertEquals(1L, histogram.bins()[0]);
        assertEquals(1L, histogram.bins()[1]);
        assertEquals(0L, histogram.under());
    }

    @Test
    void theExtremesAreTrackedEvenWhenTheyFallOutsideTheRange() {
        ScoreHistogram histogram = tenWideBins();
        histogram.record(-400.0);
        histogram.record(-50.0);
        histogram.record(900.0);
        assertEquals(-400.0, histogram.lowest());
        assertEquals(900.0, histogram.highest());
        assertTrue(histogram.under() > 0L && histogram.over() > 0L,
                "both ends overflowed, which is how you learn the range was wrong");
    }

    @Test
    void mergingIsHowTheWorkersAddUp() {
        ScoreHistogram whole = tenWideBins();
        ScoreHistogram one = whole.emptyCopy();
        ScoreHistogram two = whole.emptyCopy();
        one.record(-95.0);
        one.record(-1000.0);
        two.record(-95.0);
        two.record(-15.0);
        whole.merge(one);
        whole.merge(two);
        assertEquals(4L, whole.counted());
        assertEquals(2L, whole.bins()[0]);
        assertEquals(1L, whole.bins()[8]);
        assertEquals(1L, whole.under());
        assertEquals(-1000.0, whole.lowest());
    }

    @Test
    void histogramsOverDifferentBinsRefuseToMerge() {
        assertThrows(IllegalArgumentException.class,
                () -> tenWideBins().merge(new ScoreHistogram(-100.0, 0.0, 5.0)));
    }

    @Test
    void aScoreThatIsNotANumberIsRefusedRatherThanDropped() {
        assertThrows(IllegalArgumentException.class, () -> tenWideBins().record(Double.NaN));
    }

    @Test
    void renderKeepsOnlyTheBinsThatSawSomething() {
        ScoreHistogram histogram = tenWideBins();
        histogram.record(-95.0);
        histogram.record(-95.0);
        String rendered = histogram.render();
        assertTrue(rendered.contains("counted=2"), rendered);
        assertTrue(rendered.contains("\n0\t-100.0000\t2\n"), rendered);
        assertEquals(1L, rendered.lines().filter(line -> line.startsWith("0\t") || line.startsWith("1\t")).count(),
                "nine empty bins have no business being in the file");
    }

    @Test
    void anImpossibleRangeIsRefusedUpFront() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreHistogram(0.0, -1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new ScoreHistogram(-1.0, 1.0, 0.0));
    }
}
