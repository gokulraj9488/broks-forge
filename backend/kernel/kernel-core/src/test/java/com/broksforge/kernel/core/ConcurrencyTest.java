package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.KernelException;
import com.broksforge.kernel.core.log.LogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Concurrency: the per-org total order holds under parallel writers, and CAS has one winner. */
class ConcurrencyTest {

    @Test
    @DisplayName("parallel appends produce a gapless, unique total order")
    void parallelAppends() throws Exception {
        ForgeKernel k = Fixtures.kernel();
        int threads = 16;
        int perThread = 50;
        int total = threads * perThread;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            for (int t = 0; t < threads; t++) {
                final int base = t;
                pool.submit(() -> {
                    await(start);
                    for (int i = 0; i < perThread; i++) {
                        k.append(Fixtures.ORG,
                                new AppendCommand.CreateNode(Fixtures.prompt("t" + base + "-" + i)), Fixtures.ACTOR);
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        List<LogEntry> entries = k.log(Fixtures.ORG);
        assertEquals(total, entries.size());
        Set<Long> positions = new TreeSet<>();
        for (LogEntry e : entries) {
            positions.add(e.position().value());
        }
        assertEquals(total, positions.size());               // unique
        assertEquals(1L, positions.iterator().next());        // starts at 1
        assertEquals((long) total, new TreeSet<>(positions).last()); // gapless to N
        assertTrue(k.verifyChain(Fixtures.ORG));              // chain intact under concurrency
    }

    @Test
    @DisplayName("a compare-and-swap race has exactly one winner")
    void casRace() throws Exception {
        ForgeKernel k = Fixtures.kernel();
        Address.Revision v1 = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("v1")), Fixtures.ACTOR)
                .address().orElseThrow();
        Name prod = Name.of("prod");

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger casFailures = new AtomicInteger();
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    await(start);
                    try {
                        k.append(Fixtures.ORG, new AppendCommand.RepointName(prod, v1, null), Fixtures.ACTOR);
                        wins.incrementAndGet();
                    } catch (KernelException e) {
                        if (e.reason() == KernelException.Reason.CAS_FAILURE) {
                            casFailures.incrementAndGet();
                        }
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, wins.get());
        assertEquals(threads - 1, casFailures.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
