package io.github.qnicondavid.byteenigma.search;

/**
 * Called at intervals while a sweep runs, so a long search can say where it is.
 *
 * <p>Invoked on a daemon thread of the sweep's own, never on a worker, so a slow listener costs
 * throughput nothing. It may still be called once after the workers have finished.
 */
@FunctionalInterface
public interface SweepProgress {

    /**
     * @param keysTried     keys finished so far across all threads
     * @param keysTotal     size of the range being swept
     * @param keysPerSecond throughput since the sweep started
     * @param elapsedSeconds wall-clock time since the sweep started
     */
    void report(long keysTried, long keysTotal, double keysPerSecond, double elapsedSeconds);
}
