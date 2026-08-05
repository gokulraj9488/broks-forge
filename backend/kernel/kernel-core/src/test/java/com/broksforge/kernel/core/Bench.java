package com.broksforge.kernel.core;

import java.util.Arrays;

/**
 * A small, disciplined micro-benchmark harness (the JMH library is unavailable in the offline build,
 * so this provides JMH-style measurement: warmup, steady-state measurement, and latency percentiles).
 * Not a substitute for JMH's rigor (no separate forks, no blackhole/dead-code elimination guards
 * beyond result consumption), but sufficient to publish honest, reproducible numbers.
 */
final class Bench {

    private long consumer; // consume results so the JIT cannot eliminate the measured work

    /** Measures per-call latency: warms up, then times {@code iters} calls individually. */
    LatencyResult latency(String name, int warmup, int iters, Runnable op) {
        for (int i = 0; i < warmup; i++) {
            op.run();
        }
        long[] nanos = new long[iters];
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            op.run();
            nanos[i] = System.nanoTime() - t0;
        }
        consumer += nanos[iters - 1];
        Arrays.sort(nanos);
        double avg = Arrays.stream(nanos).average().orElse(0);
        return new LatencyResult(name, iters, avg, nanos[(int) (iters * 0.50)],
                nanos[(int) (iters * 0.95)], nanos[Math.min(iters - 1, (int) (iters * 0.99))]);
    }

    /** Measures throughput: warms up, then times a batch of {@code iters} calls as a whole. */
    ThroughputResult throughput(String name, int warmup, int iters, Runnable op) {
        for (int i = 0; i < warmup; i++) {
            op.run();
        }
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            op.run();
        }
        long elapsed = System.nanoTime() - t0;
        consumer += elapsed;
        double opsPerSec = iters / (elapsed / 1_000_000_000.0);
        return new ThroughputResult(name, iters, opsPerSec, elapsed / 1_000_000.0);
    }

    long consumer() {
        return consumer;
    }

    record LatencyResult(String name, int samples, double avgNanos, long p50Nanos, long p95Nanos, long p99Nanos) {
        @Override
        public String toString() {
            return String.format("%-26s avg %8.0f ns   p50 %8d ns   p95 %8d ns   p99 %8d ns   (n=%d)",
                    name, avgNanos, p50Nanos, p95Nanos, p99Nanos, samples);
        }
    }

    record ThroughputResult(String name, int ops, double opsPerSec, double totalMillis) {
        @Override
        public String toString() {
            return String.format("%-26s %,12.0f ops/sec   (%d ops in %.1f ms)", name, opsPerSec, ops, totalMillis);
        }
    }
}
