package io.github.qnicondavid.byteenigma.search;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
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

    /**
     * @param subjectFactory produces one subject per worker thread; must return a fresh instance each call
     * @param topN           how many candidates to keep, at least one
     */
    public SeedSweep(Supplier<T> subjectFactory, int topN) {
        this(subjectFactory, topN, null, 0L);
    }

    private SeedSweep(Supplier<T> subjectFactory, int topN, SweepProgress progress, long progressIntervalMillis) {
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be at least 1 but was " + topN);
        }
        this.subjectFactory = subjectFactory;
        this.topN = topN;
        this.progress = progress;
        this.progressIntervalMillis = progressIntervalMillis;
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
        return new SeedSweep<>(subjectFactory, topN, listener, intervalMillis);
    }

    /** Sweeps {@code [from, to)} on the calling thread. */
    public SweepResult sweep(long from, long to, byte[] ciphertext, SeedEvaluator<T> evaluator) {
        requireRange(from, to);
        long began = System.nanoTime();
        Leaderboard board = new Leaderboard(topN);
        byte[] scratch = new byte[ciphertext.length];
        T subject = subjectFactory.get();
        for (long key = from; key < to; key++) {
            Candidate candidate = evaluator.evaluate((int) key, subject, ciphertext, scratch);
            if (candidate != null) {
                board.offer(candidate);
            }
        }
        return new SweepResult(board.drainDescending(), to - from, System.nanoTime() - began);
    }

    /** Sweeps {@code [from, to)} across every available processor. */
    public SweepResult sweepParallel(long from, long to, byte[] ciphertext, SeedEvaluator<T> evaluator) {
        return sweepParallel(from, to, ciphertext, evaluator, Runtime.getRuntime().availableProcessors());
    }

    /** Sweeps {@code [from, to)} across {@code threads} workers. */
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
        List<Worker> workers = new ArrayList<>(threads);
        List<Thread> pool = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            Worker worker = new Worker(cursor, to, ciphertext, evaluator, completed);
            workers.add(worker);
            pool.add(new Thread(worker, "seed-sweep-" + i));
        }

        Thread reporter = startReporter(began, total, completed);
        pool.forEach(Thread::start);
        joinAll(pool);
        if (reporter != null) {
            reporter.interrupt();
        }
        long elapsed = System.nanoTime() - began;

        Leaderboard merged = new Leaderboard(topN);
        for (Worker worker : workers) {
            worker.localBest().forEach(merged::offer);
        }
        if (progress != null) {
            progress.report(total, total, total / (elapsed / 1_000_000_000.0), elapsed / 1_000_000_000.0);
        }
        return new SweepResult(merged.drainDescending(), total, elapsed);
    }

    private Thread startReporter(long began, long total, AtomicLong completed) {
        if (progress == null) {
            return null;
        }
        Thread reporter = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(progressIntervalMillis);
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

    private final class Worker implements Runnable {

        private final AtomicLong cursor;
        private final long end;
        private final byte[] ciphertext;
        private final SeedEvaluator<T> evaluator;
        private final AtomicLong completed;
        private final Leaderboard best = new Leaderboard(topN);
        private final byte[] scratch;

        private Worker(AtomicLong cursor, long end, byte[] ciphertext,
                       SeedEvaluator<T> evaluator, AtomicLong completed) {
            this.cursor = cursor;
            this.end = end;
            this.ciphertext = ciphertext;
            this.evaluator = evaluator;
            this.completed = completed;
            this.scratch = new byte[ciphertext.length];
        }

        @Override
        public void run() {
            T subject = subjectFactory.get();
            while (true) {
                long chunkStart = cursor.getAndAdd(CHUNK);
                if (chunkStart >= end) {
                    return;
                }
                long chunkEnd = Math.min(chunkStart + CHUNK, end);
                for (long key = chunkStart; key < chunkEnd; key++) {
                    Candidate candidate = evaluator.evaluate((int) key, subject, ciphertext, scratch);
                    if (candidate != null) {
                        best.offer(candidate);
                    }
                }
                completed.addAndGet(chunkEnd - chunkStart);
            }
        }

        private List<Candidate> localBest() {
            return best.drainDescending();
        }
    }

    /** A bounded min-heap: the weakest kept candidate sits on top, ready to be evicted. */
    private static final class Leaderboard {

        private final int capacity;
        private final PriorityQueue<Candidate> heap;

        private Leaderboard(int capacity) {
            this.capacity = capacity;
            this.heap = new PriorityQueue<>(capacity, Candidate.BY_SCORE);
        }

        private void offer(Candidate candidate) {
            if (heap.size() < capacity) {
                heap.offer(candidate);
            } else if (Candidate.BY_SCORE.compare(candidate, heap.peek()) > 0) {
                heap.poll();
                heap.offer(candidate);
            }
        }

        private List<Candidate> drainDescending() {
            List<Candidate> ordered = new ArrayList<>(heap);
            ordered.sort(Candidate.BY_SCORE.reversed());
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
    }

    private static void joinAll(List<Thread> pool) {
        for (Thread thread : pool) {
            try {
                thread.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("sweep interrupted while joining workers", interrupted);
            }
        }
    }
}
