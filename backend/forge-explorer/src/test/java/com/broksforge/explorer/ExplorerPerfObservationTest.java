package com.broksforge.explorer;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.explorer.graph.GraphModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A light application-level performance observation, measured through the {@link ForgeExplorer} facade
 * (not the raw kernel), to confirm the userspace layer adds no material overhead. It prints coarse
 * throughput/latency numbers for the operations an interactive explorer performs most; it is not a
 * rigorous JMH benchmark (the kernel owns those) and asserts only that the work completes correctly.
 */
class ExplorerPerfObservationTest {

    private static final int N = 3000;

    @Test
    @DisplayName("facade throughput/latency observation over a few thousand operations")
    void observe() {
        ForgeExplorer forge = TestKernel.explorer();

        long t0 = System.nanoTime();
        Handle root = forge.createArtifact("prompt",
                CanonicalValue.objectBuilder().put("text", "root").build());
        Handle prev = root;
        for (int i = 1; i < N; i++) {
            prev = forge.createArtifact("agent",
                    CanonicalValue.objectBuilder().put("i", CanonicalValue.of(i)).build(),
                    Ref.of(Verbs.USES, prev.hash()));
        }
        double appendMs = ms(t0);

        Name head = Name.of("chain/head");
        forge.deploy(head, prev);
        long t1 = System.nanoTime();
        for (int i = 0; i < N; i++) {
            forge.resolve(head);
        }
        double resolveUs = us(t1) / N;

        long t2 = System.nanoTime();
        var closure = forge.closure(prev.hash());
        double closureMs = ms(t2);

        long t3 = System.nanoTime();
        forge.diff(root.hash(), prev.hash());
        double diffUs = us(t3);

        long t4 = System.nanoTime();
        GraphModel model = GraphModel.of(forge.kernel(), forge.org());
        double foldMs = ms(t4);

        System.out.printf("%n=== Forge Explorer — facade performance observation (N=%d) ===%n", N);
        System.out.printf("  append (create+ref)   %,10.0f ops/sec   (%d in %.1f ms)%n",
                N / (appendMs / 1000.0), N, appendMs);
        System.out.printf("  resolve (name)        %,10.2f µs/op%n", resolveUs);
        System.out.printf("  closure (%d-deep)      %,10.2f ms%n", closure.size(), closureMs);
        System.out.printf("  diff (root vs head)   %,10.2f µs%n", diffUs);
        System.out.printf("  fold graph from log   %,10.2f ms   (%d nodes)%n", foldMs, model.nodes().size());

        assertEquals(N, model.nodes().size());
        assertEquals(N, closure.size(), "a linear composition chain of N nodes has a closure of N");
        assertEquals(prev.address(), forge.resolve(head).orElseThrow());
        assertTrue(forge.verifyChain());
        Address.Revision resolved = forge.resolve(head).orElseThrow();
        assertEquals(prev.hash(), resolved.revision());
    }

    private static double ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static double us(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000.0;
    }
}
