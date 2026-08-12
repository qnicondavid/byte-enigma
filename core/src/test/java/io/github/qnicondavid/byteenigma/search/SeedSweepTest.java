package io.github.qnicondavid.byteenigma.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * The sweep on its own, with no cipher anywhere near it. If these pass with an evaluator that
 * just does arithmetic, the "reusable keyspace search" claim in the README is not a stretch.
 */
class SeedSweepTest {

    /** Scores a key by how close it is to a target, so the best candidate is known in advance. */
    private static SeedEvaluator<int[]> closenessTo(int target) {
        return (key, subject, ciphertext, scratch) -> {
            subject[0] = key;
            return Candidate.of(key, -Math.abs((long) key - target), scratch, scratch.length);
        };
    }

    @Test
    void findsTheBestKeyInARange() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        SweepResult result = sweep.sweep(-500, 500, new byte[8], closenessTo(123));
        assertNotNull(result.top());
        assertEquals(123, result.top().key());
        assertEquals(1000, result.keysTried());
    }

    @Test
    void parallelFindsTheSameThingAsSerial() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        byte[] ciphertext = new byte[16];
        SweepResult serial = sweep.sweep(-100_000, 100_000, ciphertext, closenessTo(7777));
        SweepResult parallel = sweep.sweepParallel(-100_000, 100_000, ciphertext, closenessTo(7777), 4);
        assertEquals(serial.top().key(), parallel.top().key());
        assertEquals(serial.keysTried(), parallel.keysTried());
    }

    @Test
    void keepsTheTopNInDescendingOrder() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 5);
        SweepResult result = sweep.sweepParallel(-50_000, 50_000, new byte[8], closenessTo(0), 4);
        List<Candidate> best = result.best();
        assertEquals(5, best.size());
        for (int i = 1; i < best.size(); i++) {
            assertTrue(best.get(i - 1).score() >= best.get(i).score(), "candidates are out of order");
        }
        assertEquals(0, best.get(0).key());
    }

    @Test
    void anEvaluatorThatRejectsEverythingYieldsNothing() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        SweepResult result = sweep.sweepParallel(0, 20_000, new byte[8],
                (key, subject, ciphertext, scratch) -> null, 4);
        assertNull(result.top());
        assertTrue(result.best().isEmpty());
        assertEquals(20_000, result.keysTried());
    }

    @Test
    void everyKeyInTheRangeIsVisitedExactlyOnce() {
        AtomicLong sum = new AtomicLong();
        AtomicInteger count = new AtomicInteger();
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        sweep.sweepParallel(-30_000, 30_000, new byte[4], (key, subject, ciphertext, scratch) -> {
            sum.addAndGet(key);
            count.incrementAndGet();
            return null;
        }, 4);
        assertEquals(60_000, count.get());
        long expected = 0;
        for (long key = -30_000; key < 30_000; key++) {
            expected += key;
        }
        assertEquals(expected, sum.get());
    }

    @Test
    void eachWorkerGetsItsOwnSubject() {
        AtomicInteger built = new AtomicInteger();
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> {
            built.incrementAndGet();
            return new int[1];
        }, 1);
        sweep.sweepParallel(0, 100_000, new byte[4], closenessTo(1), 4);
        assertEquals(4, built.get(), "the sweep should build one subject per worker");
    }

    @Test
    void progressIsReportedAndEndsAtOneHundredPercent() throws InterruptedException {
        AtomicLong lastTried = new AtomicLong(-1);
        AtomicInteger reports = new AtomicInteger();
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1)
                .reportingTo((tried, total, rate, elapsed) -> {
                    lastTried.set(tried);
                    reports.incrementAndGet();
                }, 5L);
        SweepResult result = sweep.sweepParallel(0, 400_000, new byte[8], (key, subject, ct, scratch) -> {
            Thread.onSpinWait();
            return null;
        }, 2);
        assertTrue(reports.get() >= 1, "expected at least the final report");
        assertEquals(result.keysTried(), lastTried.get(), "the last report should be the whole range");
    }

    @Test
    void rangesThatCannotBeSweptAreRejected() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        byte[] ciphertext = new byte[4];
        SeedEvaluator<int[]> evaluator = closenessTo(0);
        assertRejected(() -> sweep.sweep(10, 5, ciphertext, evaluator));
        assertRejected(() -> sweep.sweep(Integer.MIN_VALUE, (1L << 33), ciphertext, evaluator));
        assertRejected(() -> sweep.sweepParallel(0, 10, ciphertext, evaluator, 0));
        assertRejected(() -> new SeedSweep<>(() -> new int[1], 0));
    }

    @Test
    void anEmptyRangeIsLegalAndFindsNothing() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        SweepResult result = sweep.sweep(42, 42, new byte[4], closenessTo(42));
        assertEquals(0, result.keysTried());
        assertNull(result.top());
    }

    @Test
    void theKeyspaceConstantsSpanExactlyTwoToThe32() {
        assertEquals(1L << 32, SeedSweep.KEYSPACE_END - SeedSweep.KEYSPACE_START);
    }

    @Test
    void anEvaluatorThatThrowsFailsTheSweepRatherThanShrinkingIt() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        try {
            sweep.sweepParallel(0, 400_000, new byte[8], (key, subject, ciphertext, scratch) -> {
                if (key == 200_000) {
                    throw new IllegalStateException("evaluator blew up");
                }
                return null;
            }, 4);
            assertTrue(false, "expected the sweep to fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("worker failed"), expected.getMessage());
            assertNotNull(expected.getCause());
        }
    }

    @Test
    void keysTriedCountsWorkDoneRatherThanTheWidthOfTheRange() {
        AtomicInteger seen = new AtomicInteger();
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        SweepResult result = sweep.sweepParallel(-20_000, 20_000, new byte[8],
                (key, subject, ciphertext, scratch) -> {
                    seen.incrementAndGet();
                    return null;
                }, 4);
        assertEquals(seen.get(), (int) result.keysTried());
        assertEquals(40_000, result.keysTried());
    }

    @Test
    void workerThreadsAreDaemonsSoTheyCannotHoldTheJvmOpen() throws InterruptedException {
        AtomicInteger nonDaemon = new AtomicInteger();
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 1);
        Thread watcher = new Thread(() -> {
            for (int i = 0; i < 200; i++) {
                Thread.getAllStackTraces().keySet().stream()
                        .filter(t -> t.getName().startsWith("seed-sweep-") && !t.isDaemon())
                        .forEach(t -> nonDaemon.incrementAndGet());
                Thread.onSpinWait();
            }
        });
        watcher.start();
        sweep.sweepParallel(0, 400_000, new byte[8], closenessTo(1), 4);
        watcher.join();
        assertEquals(0, nonDaemon.get(), "sweep workers must not be able to keep the JVM alive");
    }

    @Test
    void tiedScoresResolveTheSameWayEveryRun() {
        SeedSweep<int[]> sweep = new SeedSweep<>(() -> new int[1], 3);
        SeedEvaluator<int[]> everythingTies =
                (key, subject, ciphertext, scratch) ->
                        key % 1000 == 0 ? Candidate.of(key, 7.0, scratch, scratch.length) : null;

        List<Integer> first = keysOf(sweep.sweepParallel(0, 200_000, new byte[8], everythingTies, 4));
        for (int repeat = 0; repeat < 5; repeat++) {
            assertEquals(first, keysOf(sweep.sweepParallel(0, 200_000, new byte[8], everythingTies, 4)),
                    "tied candidates came back in a different order on repeat " + repeat);
        }
        assertEquals(List.of(0, 1000, 2000), first, "ties should break towards the lower key");
    }

    private static List<Integer> keysOf(SweepResult result) {
        return result.best().stream().map(Candidate::key).toList();
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            assertTrue(false, "expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isBlank());
        }
    }
}
