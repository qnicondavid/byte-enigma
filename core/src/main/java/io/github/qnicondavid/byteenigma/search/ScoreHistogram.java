package io.github.qnicondavid.byteenigma.search;

import java.util.Locale;

/**
 * How the scores of a whole sweep are distributed.
 *
 * <p>A leaderboard answers which keys came out on top. It cannot answer the question a reader
 * actually has, which is what the other four billion looked like. This counts every score into
 * fixed-width bins so the shape can be drawn, and so a margin can be read against the distribution
 * it stands outside of rather than against nine neighbours.
 *
 * <p>A sweep cannot make two passes over four billion keys to find its own range, so the range is
 * fixed up front and anything outside it lands in {@link #under()} or {@link #over()}. The extremes
 * are tracked separately, which is how you find out that the range was wrong.
 *
 * <p>Not thread-safe. Each worker records into its own copy from {@link #emptyCopy()} and the copies
 * are merged once the workers have stopped, the same arrangement the leaderboard uses.
 */
public final class ScoreHistogram {

    private final double lo;
    private final double hi;
    private final double binWidth;
    private final long[] bins;

    private long counted;
    private long under;
    private long over;
    private double lowest = Double.POSITIVE_INFINITY;
    private double highest = Double.NEGATIVE_INFINITY;

    /**
     * @param lo       the low edge of the first bin
     * @param hi       the high edge of the last bin, above {@code lo}
     * @param binWidth how wide each bin is, positive, and a divisor of the range if you want the
     *                 last bin to end exactly at {@code hi}
     */
    public ScoreHistogram(double lo, double hi, double binWidth) {
        if (!(hi > lo)) {
            throw new IllegalArgumentException("hi must be above lo but was " + hi + " against " + lo);
        }
        if (!(binWidth > 0.0)) {
            throw new IllegalArgumentException("binWidth must be positive but was " + binWidth);
        }
        int count = (int) Math.ceil((hi - lo) / binWidth);
        if (count < 1 || count > 1 << 24) {
            throw new IllegalArgumentException("that range and width need " + count + " bins");
        }
        this.lo = lo;
        this.hi = hi;
        this.binWidth = binWidth;
        this.bins = new long[count];
    }

    /** Another histogram over the same bins, for one worker to fill on its own. */
    public ScoreHistogram emptyCopy() {
        return new ScoreHistogram(lo, hi, binWidth);
    }

    /** Counts one score. A score of {@code NaN} is rejected rather than quietly dropped. */
    public void record(double score) {
        if (Double.isNaN(score)) {
            throw new IllegalArgumentException("a candidate scored NaN, which no bin can hold");
        }
        counted++;
        if (score < lowest) {
            lowest = score;
        }
        if (score > highest) {
            highest = score;
        }
        if (score < lo) {
            under++;
            return;
        }
        int index = (int) Math.floor((score - lo) / binWidth);
        if (index >= bins.length) {
            over++;
            return;
        }
        bins[index]++;
    }

    /** Folds another histogram over the same bins into this one. Call it after the workers stop. */
    public void merge(ScoreHistogram other) {
        if (other.bins.length != bins.length || other.lo != lo || other.binWidth != binWidth) {
            throw new IllegalArgumentException("histograms over different bins cannot be merged");
        }
        for (int i = 0; i < bins.length; i++) {
            bins[i] += other.bins[i];
        }
        counted += other.counted;
        under += other.under;
        over += other.over;
        lowest = Math.min(lowest, other.lowest);
        highest = Math.max(highest, other.highest);
    }

    /** How many scores were counted, including the ones that fell outside the range. */
    public long counted() {
        return counted;
    }

    /** How many fell below the low edge. Anything but zero means the range was chosen badly. */
    public long under() {
        return under;
    }

    /** How many fell above the high edge. */
    public long over() {
        return over;
    }

    /** The lowest score seen, or positive infinity if nothing was recorded. */
    public double lowest() {
        return lowest;
    }

    /** The highest score seen, or negative infinity if nothing was recorded. */
    public double highest() {
        return highest;
    }

    /** The counts, one per bin, low edge first. */
    public long[] bins() {
        return bins.clone();
    }

    /**
     * The committed data format: a header of scalars, then one line per bin that holds anything.
     *
     * <p>Empty bins are left out because most of them are, and a file of six thousand zeroes is a
     * worse record than a file of the bins that saw something.
     */
    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("# byte-enigma score histogram\n");
        out.append("lo=").append(Double.toString(lo)).append('\n');
        out.append("hi=").append(Double.toString(hi)).append('\n');
        out.append("binWidth=").append(Double.toString(binWidth)).append('\n');
        out.append("counted=").append(counted).append('\n');
        out.append("under=").append(under).append('\n');
        out.append("over=").append(over).append('\n');
        out.append("lowest=").append(counted == 0L ? "none" : Double.toString(lowest)).append('\n');
        out.append("highest=").append(counted == 0L ? "none" : Double.toString(highest)).append('\n');
        for (int i = 0; i < bins.length; i++) {
            if (bins[i] != 0L) {
                out.append(String.format(Locale.ROOT, "%d\t%.4f\t%d", i, lo + i * binWidth, bins[i]))
                        .append('\n');
            }
        }
        return out.toString();
    }
}
