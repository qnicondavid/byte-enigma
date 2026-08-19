package io.github.qnicondavid.byteenigma.search;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Walks a range of 32-bit keys, hands each one to an evaluator, and keeps the best few.
 *
 * <p>Nothing here knows what a rotor is. The sweep owns the range, the worker threads, the
 * leaderboard and the clock; the {@link SeedEvaluator} owns everything else. Point it at a
 * different {@code Supplier} and a different evaluator and it will brute-force something else
 * for you.
 *
 * <h2>Threading</h2>
 *
 * <p>Each worker pulls chunks of the range off a shared cursor and calls the evaluator with a
 * subject and a scratch buffer nobody else touches. That is why the constructor takes a
 * {@code Supplier} rather than an instance: the sweep needs one subject per thread, and taking
 * a factory makes it impossible to accidentally share a mutable cipher across workers.
 *
 * <p>Instances are immutable and safe to reuse across sweeps.
 *
 * <h2>What the result counts</h2>
 *
 * <p>{@link SweepResult#keysTried()} is the number of keys workers actually finished, not the
 * width of the range they were given. If an evaluator throws, the sweep stops the other workers
 * and rethrows rather than returning a result that would claim coverage it does not have. A
 * throughput figure from this class is always a figure for work that really happened.
 *
 * @param <T> what the evaluator rekeys and applies
 */
public final class SeedSweep<T> {

    /** The whole 32-bit keyspace, as a half-open range you can hand to {@link #sweepParallel}. */
    public static final long KEYSPACE_START = Integer.MIN_VALUE;

    /** One past the last key, so {@code KEYSPACE_END - KEYSPACE_START} is exactly 2^32. */
    public static final long KEYSPACE_END = (long) Integer.MAX_VALUE + 1L;

    private static final long CHUNK = 4096L;

    private final Supplier<T> subjectFactory;
    private final int topN;
    private final SweepProgress progress;
    private final long progressIntervalMillis;
    private final ScoreHistogram scores;

    /**
     * @param subjectFactory produces one subject per worker thread; must return a fresh instance each call
     * @param topN           how many candidates to keep, at least one
     */
    public SeedSweep(Supplier<T> subjectFactory, int topN) {
        this(subjectFactory, topN, null, 0L, null);
    }

    private SeedSweep(Supplier<T> subjectFactory, int topN, SweepProgress progress,
            long progressIntervalMillis, ScoreHistogram scores) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be at least 1 but was " + topN);
        }
        this.subjectFactory = subjectFactory;
        this.topN = topN;
        this.progress = progress;
        this.progressIntervalMillis = progressIntervalMillis;
        this.scores = scores;
    }

    /**
     * Returns a copy of this sweep that reports progress while it runs.
     *
     * <p>Only {@link #sweepParallel} reports; a single-threaded sweep has no spare thread to
     * report from and is not the thing you run for hours.
     */
    public SeedSweep<T> reportingTo(SweepProgress listener, long intervalMillis) {
        if (intervalMillis < 1L) {
            throw new IllegalArgumentException("intervalMillis must be positive but was " + intervalMillis);
        }
        return new SeedSweep<>(subjectFactory, topN, listener, intervalMillis, scores);
    }

    /**
     * Returns a copy of this sweep that counts every score into {@code target} as well as ranking
     * the best of them.
     *
     * <p>The leaderboard says which keys won. This says what they beat. Candidates the evaluator
     * rejects outright are not counted, because they were never scored.
     *
     * <p>Costs one array increment per candidate, against a candidate that costs microseconds.
     * {@code SweepBenchmark} measures it rather than assuming it.
     */
    public SeedSweep<T> recordingScoresInto(ScoreHistogram target) {
        if (target == null) {
            throw new IllegalArgumentException("pass a histogram or do not call this");
        }
        return new SeedSweep<>(subjectFactory, topN, progress, progressIntervalMillis, target);
    }

    /** Sweeps {@code [from, to)} on the calling thread. */
    public SweepResult sweep(long from, long to, byte[] ciphertext, SeedEvaluator<T> evaluator) {
        requireRange(from, to);
        long began = System.nanoTime();
        Leaderboard board = new Leaderboard(topN);
        byte[] scratch = new byte[ciphertext.length];
        T subject = subjectFactory.get();
        long tried = 0L;
        for (long key = from; key < to; key++) {
            Candidate candidate = evaluator.evaluate((int) key, subject, ciphertext, scratch);
            if (candidate != null) {
                board.offer(candidate);
                if (scores != null) {
                    scores.record(candidate.score());
                }
            }
            tried++;
        }
        return new SweepResult(board.drainDescending(), tried, System.nanoTime() - began);
    }

    /** Sweeps {@code [from, to)} across every available processor. */
    public SweepResult sweepParallel(long from, long to, byte[] ciphertext, SeedEvaluator<T> evaluator) {
        return sweepParallel(from, to, ciphertext, evaluator, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Sweeps {@code [from, to)} across {@code threads} workers.
     *
     * @throws IllegalStateException if a worker died or the calling thread was interrupted; in
     *         either case the other workers are stopped first
     */
    public SweepResult sweepParallel(long from, long to, byte[] ciphertext, SeedEvaluator<T> evaluator, int threads) {
        requireRange(from, to);
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1 but was " + threads);
        }
        long total = to - from;
        if (threads == 1 || total <= CHUNK) {
            return sweep(from, to, ciphertext, evaluator);
        }

        long began = System.nanoTime();
        AtomicLong cursor = new AtomicLong(from);
        AtomicLong completed = new AtomicLong();
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        List<Worker> workers = new ArrayList<>(threads);
        List<Thread> pool = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            Worker worker = new Worker(cursor, to, ciphertext, evaluator, completed, stop, failure);
            Thread thread = new Thread(worker, "seed-sweep-" + i);
            thread.setDaemon(true);
            workers.add(worker);
            pool.add(thread);
        }

        Thread reporter = startReporter(began, total, completed, stop);
        pool.forEach(Thread::start);
        try {
            joinAll(pool);
        } catch (InterruptedException interrupted) {
            stop.set(true);
            joinQuietly(pool);
            stopReporter(reporter);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("sweep interrupted; workers stopped after "
                    + completed.get() + " keys", interrupted);
        }
        stopReporter(reporter);
        long elapsed = System.nanoTime() - began;

        Throwable died = failure.get();
        if (died != null) {
            throw new IllegalStateException("a sweep worker failed after " + completed.get()
                    + " of " + total + " keys", died);
        }

        Leaderboard merged = new Leaderboard(topN);
        for (Worker worker : workers) {
            worker.localBest().forEach(merged::offer);
            if (scores != null) {
                scores.merge(worker.localScores);
            }
        }
        long tried = completed.get();
        if (progress != null) {
            double seconds = elapsed / 1_000_000_000.0;
            progress.report(tried, total, seconds > 0.0 ? tried / seconds : 0.0, seconds);
        }
        return new SweepResult(merged.drainDescending(), tried, elapsed);
    }

    private Thread startReporter(long began, long total, AtomicLong completed, AtomicBoolean stop) {
        if (progress == null) {
            return null;
        }
        Thread reporter = new Thread(() -> {
            try {
                while (!stop.get()) {
                    Thread.sleep(progressIntervalMillis);
                    if (stop.get()) {
                        return;
                    }
                    long done = completed.get();
                    double seconds = (System.nanoTime() - began) / 1_000_000_000.0;
                    progress.report(done, total, seconds > 0.0 ? done / seconds : 0.0, seconds);
                }
            } catch (InterruptedException finished) {
                Thread.currentThread().interrupt();
            }
        }, "seed-sweep-progress");
        reporter.setDaemon(true);
        reporter.start();
        return reporter;
    }

    /**
     * Stops the reporter and waits for it to leave the listener, so the caller's closing report is
     * the last one the listener sees rather than racing a stale one.
     */
    private void stopReporter(Thread reporter) {
        if (reporter == null) {
            return;
        }
        reporter.interrupt();
        try {
            reporter.join(1000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private final class Worker implements Runnable {

        private final AtomicLong cursor;
        private final long end;
        private final byte[] ciphertext;
        private final SeedEvaluator<T> evaluator;
        private final AtomicLong completed;
        private final AtomicBoolean stop;
        private final AtomicReference<Throwable> failure;
        private final Leaderboard best = new Leaderboard(topN);
        private final ScoreHistogram localScores = scores == null ? null : scores.emptyCopy();
        private final byte[] scratch;

        private Worker(AtomicLong cursor, long end, byte[] ciphertext, SeedEvaluator<T> evaluator,
                       AtomicLong completed, AtomicBoolean stop, AtomicReference<Throwable> failure) {
            this.cursor = cursor;
            this.end = end;
            this.ciphertext = ciphertext;
            this.evaluator = evaluator;
            this.completed = completed;
            this.stop = stop;
            this.failure = failure;
            this.scratch = new byte[ciphertext.length];
        }

        @Override
        public void run() {
            try {
                T subject = subjectFactory.get();
                while (!stop.get()) {
                    long chunkStart = cursor.getAndAdd(CHUNK);
                    if (chunkStart >= end) {
                        return;
                    }
                    long chunkEnd = Math.min(chunkStart + CHUNK, end);
                    for (long key = chunkStart; key < chunkEnd; key++) {
                        Candidate candidate = evaluator.evaluate((int) key, subject, ciphertext, scratch);
                        if (candidate != null) {
                            best.offer(candidate);
                            if (localScores != null) {
                                localScores.record(candidate.score());
                            }
                        }
                    }
                    completed.addAndGet(chunkEnd - chunkStart);
                }
            } catch (Throwable thrown) {
                failure.compareAndSet(null, thrown);
                stop.set(true);
            }
        }

        private List<Candidate> localBest() {
            return best.drainDescending();
        }
    }

    /**
     * A bounded min-heap: the weakest kept candidate sits on top, ready to be evicted.
     *
     * <p>Ties break towards the lower key, so a sweep that finds several equally good candidates
     * returns the same ones whichever order the threads happened to find them in. That matters for
     * a crib, where every hit scores exactly the crib length and ties are the normal case if the
     * crib is short.
     */
    private static final class Leaderboard {

        private final int capacity;
        private final PriorityQueue<Candidate> heap;

        private Leaderboard(int capacity) {
            this.capacity = capacity;
            this.heap = new PriorityQueue<>(capacity, Candidate.WEAKEST_FIRST);
        }

        /**
         * Called once per key, so the common case is spelled out rather than routed through a
         * chained comparator. The condition is exactly {@code WEAKEST_FIRST.compare(candidate,
         * weakest) > 0}; {@code CandidateTest.theLeaderboardsFastPathAgreesWithTheComparator}
         * pins that the two agree.
         */
        private void offer(Candidate candidate) {
            if (heap.size() < capacity) {
                heap.offer(candidate);
                return;
            }
            Candidate weakest = heap.peek();
            double score = candidate.score();
            double weakestScore = weakest.score();
            if (score > weakestScore || (score == weakestScore && candidate.key() < weakest.key())) {
                heap.poll();
                heap.offer(candidate);
            }
        }

        private List<Candidate> drainDescending() {
            List<Candidate> ordered = new ArrayList<>(heap);
            ordered.sort(Candidate.WEAKEST_FIRST.reversed());
            return ordered;
        }
    }

    private static void requireRange(long from, long to) {
        if (to < from) {
            throw new IllegalArgumentException("to must be at least from, got [" + from + ", " + to + ")");
        }
        if (to - from > (1L << 32)) {
            throw new IllegalArgumentException("range is wider than the 2^32 keyspace");
        }
        // Keys are cast to int in the loops above, so an endpoint outside the keyspace wraps and
        // the sweep reports keys it never tried. This is the library half, so it checks for itself.
        if (from < KEYSPACE_START || to > KEYSPACE_END) {
            throw new IllegalArgumentException("range must lie inside [" + KEYSPACE_START + ", "
                    + KEYSPACE_END + "), got [" + from + ", " + to + ")");
        }
    }

    private static void joinAll(List<Thread> pool) throws InterruptedException {
        for (Thread thread : pool) {
            thread.join();
        }
    }

    private static void joinQuietly(List<Thread> pool) {
        for (Thread thread : pool) {
            try {
                thread.join(2000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
