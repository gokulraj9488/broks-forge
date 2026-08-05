package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.memory.InMemoryGraphIndex;
import com.broksforge.kernel.core.memory.InMemoryNameStore;
import com.broksforge.kernel.core.memory.InMemoryRevisionStore;
import com.broksforge.kernel.core.op.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory performance benchmarks for the six operations plus projection rebuild. Gated on the
 * {@code KERNEL_BENCH=1} environment variable so it never slows the normal build; run explicitly with
 * that variable set. Results are printed to standard out and reproduced in the RC1 report.
 *
 * <p>Methodology: a warmup phase lets the JIT reach steady state, then latency benchmarks time each
 * call individually (reporting avg/p50/p95/p99), and throughput benchmarks time a batch. Results are
 * consumed so the JIT cannot eliminate the measured work. This is JMH-style measurement without the
 * JMH library (unavailable offline) — honest and reproducible, though without JMH's separate forks.
 */
@EnabledIfEnvironmentVariable(named = "KERNEL_BENCH", matches = "1")
class KernelBenchmark {

    private static final com.broksforge.kernel.api.OrgId ORG = Fixtures.ORG;
    private static final com.broksforge.kernel.api.ActorId ACTOR = Fixtures.ACTOR;

    @Test
    @DisplayName("in-memory benchmarks")
    void inMemory() {
        Bench bench = new Bench();
        System.out.println("\n=== Forge Kernel — in-memory benchmarks ===");
        System.out.println("JVM: " + System.getProperty("java.version") + "  OS: "
                + System.getProperty("os.name") + "  cores: " + Runtime.getRuntime().availableProcessors());

        // append throughput
        ForgeKernel k1 = Fixtures.kernel();
        AtomicInteger n = new AtomicInteger();
        System.out.println(bench.throughput("append (CreateNode)", 2_000, 20_000,
                () -> k1.append(ORG, new AppendCommand.CreateNode(Fixtures.prompt("p" + n.incrementAndGet())), ACTOR)));

        // resolve latency
        ForgeKernel k2 = Fixtures.kernel();
        Address.Revision v = (Address.Revision) k2.append(ORG,
                new AppendCommand.CreateNode(Fixtures.prompt("v")), ACTOR).address().orElseThrow();
        k2.append(ORG, new AppendCommand.RepointName(Name.of("prod"), v, null), ACTOR);
        Name prod = Name.of("prod");
        System.out.println(bench.latency("resolve (current)", 5_000, 50_000, () -> k2.resolve(ORG, prod)));

        // diff latency
        ForgeKernel k3 = Fixtures.kernel();
        Address.Revision a = (Address.Revision) k3.append(ORG,
                new AppendCommand.CreateNode(Fixtures.prompt("hello world one")), ACTOR).address().orElseThrow();
        Address.Revision b = (Address.Revision) k3.append(ORG,
                new AppendCommand.CreateNode(Fixtures.prompt("hello world two")), ACTOR).address().orElseThrow();
        System.out.println(bench.latency("diff (2 revisions)", 5_000, 50_000,
                () -> k3.diff(a.revision(), b.revision())));

        // closure latency (agent composed of 50 components)
        ForgeKernel k4 = Fixtures.kernel();
        List<Ref> refs = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Revision c = Fixtures.prompt("component-" + i);
            k4.append(ORG, new AppendCommand.CreateNode(c), ACTOR);
            refs.add(Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), c.hash()));
        }
        Revision agent = Fixtures.artifact("agent", CanonicalValue.NULL, refs);
        Address.Revision agentAddr = (Address.Revision) k4.append(ORG,
                new AppendCommand.CreateNode(agent), ACTOR).address().orElseThrow();
        System.out.println(bench.latency("closure (50 members)", 2_000, 20_000,
                () -> k4.closure(agentAddr.revision())));

        // traverse latency (agent -> 50 components, one hop)
        System.out.println(bench.latency("traverse (depth 1, 50 edges)", 2_000, 20_000,
                () -> k4.traverse(ORG, new Query(agentAddr, java.util.Set.of(EdgeFamily.COMPOSITION),
                        Query.Direction.OUT, 1))));

        // projection rebuild: fold a 20k-entry log into fresh projections
        ForgeKernel big = Fixtures.kernel();
        for (int i = 0; i < 8_000; i++) {
            big.append(ORG, new AppendCommand.CreateNode(Fixtures.prompt("r" + i)), ACTOR);
        }
        List<LogEntry> log = big.log(ORG);
        System.out.println(bench.throughput("projection rebuild (" + log.size() + " entries)", 0, 5,
                () -> foldAll(log)));

        System.out.println("(consumer=" + bench.consumer() + ")\n");
    }

    private static void foldAll(List<LogEntry> log) {
        InMemoryRevisionStore revisions = new InMemoryRevisionStore();
        InMemoryGraphIndex graph = new InMemoryGraphIndex();
        InMemoryNameStore names = new InMemoryNameStore();
        for (LogEntry e : log) {
            if (e.payload() instanceof Payload.NodePut np) {
                revisions.put(np.revision().hash(), np.revision());
            }
            graph.apply(e);
            names.apply(e);
        }
    }
}
