package io.github.qnicondavid.byteenigma.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A duration reads the same on every machine.
 *
 * <p>Each branch is checked with the default locale set to one that writes a comma for a decimal
 * point, which is what most of Europe is set to, and put back afterwards. Without
 * {@code Locale.ROOT} inside {@code Durations} every assertion here fails on that machine and none
 * of them fails on an English one, which is how the difference went unnoticed.
 */
class DurationsTest {

    @Test
    void everyBranchReadsTheSameUnderACommaDecimalLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ro-RO"));
            assertEquals("5.9 s", Durations.format(5.9));
            assertEquals("89.9 s", Durations.format(89.9));
            assertEquals("1.5 min", Durations.format(90.0));
            assertEquals("62.2 min", Durations.format(3731.9226022));
            assertEquals("1.55 h", Durations.format(5580.0));
            assertEquals("3.79 h", Durations.format(13644.0));
            assertEquals("2.50 days", Durations.format(216000.0));
            assertEquals("unknown", Durations.format(Double.NaN));
            assertEquals("unknown", Durations.format(Double.POSITIVE_INFINITY));
        } finally {
            Locale.setDefault(original);
        }
    }
}
