package com.enigma.breaker;

import com.enigma.EnigmaMachine;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class SeedSweep {

    @FunctionalInterface
    public interface SeedEvaluator {

        Candidate evaluate(int seed, EnigmaMachine machine, byte[] ciphertext, byte[] out);
    }

    @FunctionalInterface
    public interface NecessaryCondition {

        boolean admits(int seed, byte[] decryptedWindow, int len);
    }

    public record SweepResult(List<Candidate> best, long seedsTried, long elapsedNanos) {

        public SweepResult {
            best = List.copyOf(best);
        }

        public Candidate top() {
            return best.isEmpty() ? null : best.get(0);
        }

        public double elapsedSeconds() {
            return elapsedNanos / 1_000_000_000.0;
        }

        public double keysPerSecond() {
            double seconds = elapsedSeconds();
            return seconds > 0.0 ? seedsTried / seconds : 0.0;
        }
    }

    public static final int DEFAULT_ROTOR_COUNT = 3;

    private static final long CHUNK = 4096L;

    private final int rotorCount;
    private final int topN;

    public SeedSweep() {
        this(DEFAULT_ROTOR_COUNT, 1);
    }

    public SeedSweep(int rotorCount, int topN) {
        if (rotorCount < 1) {
            throw new IllegalArgumentException("rotorCount must be >= 1");
        }
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be >= 1");
        }
        this.rotorCount = rotorCount;
        this.topN = topN;
    }

    public SweepResult sweep(long start, long end, byte[] ciphertext, SeedEvaluator evaluator) {
        requireRange(start, end);
        long began = System.nanoTime();
        BestKeeper keeper = new BestKeeper(topN);
        byte[] out = new byte[ciphertext.length];
        EnigmaMachine machine = new EnigmaMachine((int) start, rotorCount);
        long tried = 0;
        for (long s = start; s < end; s++) {
            int seed = (int) s;
            machine.rekey(seed);
            Candidate candidate = evaluator.evaluate(seed, machine, ciphertext, out);
            if (candidate != null) {
                keeper.offer(candidate);
            }
            tried++;
        }
        long elapsed = System.nanoTime() - began;
        return new SweepResult(keeper.drainDescending(), tried, elapsed);
    }

    public SweepResult sweepParallel(long start, long end, byte[] ciphertext, SeedEvaluator evaluator) {
        return sweepParallel(start, end, ciphertext, evaluator, Runtime.getRuntime().availableProcessors());
    }

    public SweepResult sweepParallel(long start, long end, byte[] ciphertext, SeedEvaluator evaluator, int threads) {
        requireRange(start, end);
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1");
        }
        long total = end - start;
        if (threads == 1 || total <= CHUNK) {
            return sweep(start, end, ciphertext, evaluator);
        }
        long began = System.nanoTime();
        AtomicLong cursor = new AtomicLong(start);
        List<Worker> workers = new ArrayList<>(threads);
        List<Thread> pool = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            Worker worker = new Worker(cursor, end, ciphertext, evaluator);
            Thread thread = new Thread(worker, "seed-sweep-" + i);
            workers.add(worker);
            pool.add(thread);
        }
        for (Thread thread : pool) {
            thread.start();
        }
        joinAll(pool);
        long elapsed = System.nanoTime() - began;

        BestKeeper merged = new BestKeeper(topN);
        long tried = 0;
        for (Worker worker : workers) {
            for (Candidate candidate : worker.localBest()) {
                merged.offer(candidate);
            }
            tried += worker.seedsTried();
        }
        return new SweepResult(merged.drainDescending(), tried, elapsed);
    }

    private final class Worker implements Runnable {

        private final AtomicLong cursor;
        private final long end;
        private final byte[] ciphertext;
        private final SeedEvaluator evaluator;
        private final BestKeeper best;
        private final byte[] out;
        private long tried;

        private Worker(AtomicLong cursor, long end, byte[] ciphertext, SeedEvaluator evaluator) {
            this.cursor = cursor;
            this.end = end;
            this.ciphertext = ciphertext;
            this.evaluator = evaluator;
            this.best = new BestKeeper(topN);
            this.out = new byte[ciphertext.length];
        }

        @Override
        public void run() {
            EnigmaMachine machine = new EnigmaMachine(0, rotorCount);
            while (true) {
                long chunkStart = cursor.getAndAdd(CHUNK);
                if (chunkStart >= end) {
                    return;
                }
                long chunkEnd = Math.min(chunkStart + CHUNK, end);
                for (long s = chunkStart; s < chunkEnd; s++) {
                    int seed = (int) s;
                    machine.rekey(seed);
                    Candidate candidate = evaluator.evaluate(seed, machine, ciphertext, out);
                    if (candidate != null) {
                        best.offer(candidate);
                    }
                    tried++;
                }
            }
        }

        private List<Candidate> localBest() {
            return best.drainDescending();
        }

        private long seedsTried() {
            return tried;
        }
    }

    private static final class BestKeeper {

        private final int capacity;
        private final PriorityQueue<Candidate> heap;

        private BestKeeper(int capacity) {
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

    private static void requireRange(long start, long end) {
        if (end < start) {
            throw new IllegalArgumentException("end must be >= start");
        }
        if (end - start > (1L << 32)) {
            throw new IllegalArgumentException("range exceeds the 2^32 seed space");
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
