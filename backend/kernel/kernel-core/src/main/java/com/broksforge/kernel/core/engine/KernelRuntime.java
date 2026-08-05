package com.broksforge.kernel.core.engine;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Provenance;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.command.AppendResult;
import com.broksforge.kernel.core.event.Subscription;
import com.broksforge.kernel.core.event.SubscriptionProgram;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.node.KindLaws;
import com.broksforge.kernel.core.op.ClosureEngine;
import com.broksforge.kernel.core.op.Delta;
import com.broksforge.kernel.core.op.DiffEngine;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.op.Subgraph;
import com.broksforge.kernel.core.op.TraverseEngine;
import com.broksforge.kernel.core.reproduce.ReproduceContext;
import com.broksforge.kernel.core.reproduce.ReproduceResult;
import com.broksforge.kernel.core.reproduce.Reproducer;
import com.broksforge.kernel.core.store.GraphIndex;
import com.broksforge.kernel.core.store.Log;
import com.broksforge.kernel.core.store.NameStore;
import com.broksforge.kernel.core.store.RevisionStore;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The in-memory Forge Kernel runtime — a complete implementation of the six operations over the
 * storage ports.
 *
 * <p>Design (docs/v2/KERNEL_IMPLEMENTATION_PLAN.md §4–§8): the {@link Log} is the sole source of
 * truth; the revision store, graph index, and name store are projections folded from it. Each append
 * is one transaction serialized by a per-org lock — validate, seal-and-append (assigning position and
 * chaining the previous hash), fold the projections — after which the committed entry is published to
 * matching subscriptions (Law 1, Law 2, Law 3, Law 8). Record time is set here (Law 8); the platform's
 * own programs append through this same path as ordinary actors (Law 9).
 *
 * <p>This class contains no Spring, no SQL, and no AI: it is a self-contained substrate.
 */
public final class KernelRuntime implements ForgeKernel {

    private static final int MAX_PUBLISH_DEPTH = 64;

    private final Log log;
    private final RevisionStore revisions;
    private final GraphIndex graph;
    private final NameStore names;
    private final ClosureEngine closureEngine;
    private final TraverseEngine traverseEngine;
    private final DiffEngine diffEngine;
    private final List<Reproducer> reproducers;
    private final Supplier<NodeId> minter;
    private final Clock clock;

    private final Map<OrgId, ReentrantLock> orgLocks = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Sub> subscriptions = new CopyOnWriteArrayList<>();
    private final ThreadLocal<Integer> publishDepth = ThreadLocal.withInitial(() -> 0);

    /**
     * @param log         the append log (truth)
     * @param revisions   the content-addressed revision store (projection)
     * @param graph       the graph index (projection)
     * @param names       the name store (projection)
     * @param reproducers the registered reproducers (may be empty)
     * @param minter      supplies fresh node ids
     * @param clock       supplies record time
     */
    public KernelRuntime(Log log, RevisionStore revisions, GraphIndex graph, NameStore names,
                         List<Reproducer> reproducers, Supplier<NodeId> minter, Clock clock) {
        this.log = Objects.requireNonNull(log, "log");
        this.revisions = Objects.requireNonNull(revisions, "revisions");
        this.graph = Objects.requireNonNull(graph, "graph");
        this.names = Objects.requireNonNull(names, "names");
        this.reproducers = List.copyOf(reproducers);
        this.minter = Objects.requireNonNull(minter, "minter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.closureEngine = new ClosureEngine(revisions);
        this.traverseEngine = new TraverseEngine(graph);
        this.diffEngine = new DiffEngine();
    }

    // ---- append ------------------------------------------------------------------------------

    @Override
    public AppendResult append(OrgId org, AppendCommand command, ActorId actor, Instant validTime) {
        Objects.requireNonNull(org, "org");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(actor, "actor");
        Instant recordTime = clock.instant();
        Provenance provenance = new Provenance(actor, validTime == null ? recordTime : validTime, recordTime);

        ReentrantLock lock = orgLocks.computeIfAbsent(org, o -> new ReentrantLock());
        AppendResult result;
        LogEntry committed;
        lock.lock();
        try {
            Prepared prepared = prepare(org, command);
            committed = log.append(org,
                    (position, prevHash) -> LogEntry.seal(org, position, prevHash, provenance, prepared.payload()));
            if (prepared.payload() instanceof Payload.NodePut np) {
                revisions.put(np.revision().hash(), np.revision());
            }
            graph.apply(committed);
            names.apply(committed);
            result = new AppendResult(committed, prepared.address());
        } finally {
            lock.unlock();
        }
        publish(committed);
        return result;
    }

    @Override
    public AppendResult append(OrgId org, AppendCommand command, ActorId actor) {
        return append(org, command, actor, null);
    }

    private Prepared prepare(OrgId org, AppendCommand command) {
        return switch (command) {
            case AppendCommand.CreateNode c -> {
                KindLaws.enforce(c.revision());
                validateRefsExist(c.revision());
                NodeId node = mintFresh(org);
                Address address = new Address.Revision(org, c.revision().kind(), node, c.revision().hash());
                yield new Prepared(new Payload.NodePut(node, c.revision()), Optional.of(address));
            }
            case AppendCommand.AddRevision c -> {
                Kind existing = graph.kindOf(org, c.node()).orElseThrow(() -> new KernelException(
                        KernelException.Reason.UNKNOWN_NODE, "unknown node: " + c.node()));
                if (existing != c.revision().kind()) {
                    throw new KernelException(KernelException.Reason.KIND_MISMATCH,
                            "revision kind " + c.revision().kind() + " != continuant kind " + existing);
                }
                KindLaws.enforce(c.revision());
                validateRefsExist(c.revision());
                Address address = new Address.Revision(org, c.revision().kind(), c.node(), c.revision().hash());
                yield new Prepared(new Payload.NodePut(c.node(), c.revision()), Optional.of(address));
            }
            case AppendCommand.AssertEdge c -> {
                requireEndpointExists(org, c.edge().from());
                requireEndpointExists(org, c.edge().to());
                yield new Prepared(new Payload.EdgeAsserted(c.edge()), Optional.empty());
            }
            case AppendCommand.RetractEdge c ->
                    new Prepared(new Payload.EdgeRetracted(c.edge()), Optional.empty());
            case AppendCommand.RepointName c -> {
                if (!revisions.contains(c.target().revision())) {
                    throw new KernelException(KernelException.Reason.MISSING_TARGET,
                            "name target revision not found: " + c.target().revision());
                }
                Address.Revision current = names.current(org, c.name()).orElse(null);
                if (!Objects.equals(current, c.expected())) {
                    throw new KernelException(KernelException.Reason.CAS_FAILURE,
                            "name '" + c.name() + "' expected " + c.expected() + " but was " + current);
                }
                Address address = new Address.NamePointer(org, c.name());
                yield new Prepared(new Payload.NameRepointed(c.name(), c.expected(), c.target()), Optional.of(address));
            }
            case AppendCommand.Tick c -> new Prepared(new Payload.ClockTick(c.at()), Optional.empty());
        };
    }

    private void validateRefsExist(Revision revision) {
        for (Ref ref : revision.refs()) {
            if (!revisions.contains(ref.target())) {
                throw new KernelException(KernelException.Reason.MISSING_REFERENCE,
                        "intrinsic reference targets unknown revision: " + ref.target());
            }
        }
    }

    private void requireEndpointExists(OrgId org, Address address) {
        switch (address) {
            case Address.Node n -> {
                if (graph.kindOf(org, n.node()).isEmpty()) {
                    throw new KernelException(KernelException.Reason.MISSING_TARGET, "unknown node: " + n.node());
                }
            }
            case Address.Revision r -> {
                if (!revisions.contains(r.revision())) {
                    throw new KernelException(KernelException.Reason.MISSING_TARGET,
                            "unknown revision: " + r.revision());
                }
            }
            case Address.NamePointer np -> {
                if (names.current(org, np.name()).isEmpty()) {
                    throw new KernelException(KernelException.Reason.MISSING_TARGET, "unknown name: " + np.name());
                }
            }
        }
    }

    private NodeId mintFresh(OrgId org) {
        for (int attempt = 0; attempt < 16; attempt++) {
            NodeId candidate = minter.get();
            if (graph.kindOf(org, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not mint a fresh node id after 16 attempts");
    }

    // ---- resolve -----------------------------------------------------------------------------

    @Override
    public Optional<Address.Revision> resolve(OrgId org, Name name) {
        return names.current(org, name);
    }

    @Override
    public Optional<Address.Revision> resolveAt(OrgId org, Name name, LogPosition asOf) {
        Address.Revision found = null;
        for (LogEntry entry : log.read(org, LogPosition.ZERO, asOf)) {
            if (entry.payload() instanceof Payload.NameRepointed nr && nr.name().equals(name)) {
                found = nr.to();
            }
        }
        return Optional.ofNullable(found);
    }

    // ---- traverse ----------------------------------------------------------------------------

    @Override
    public Subgraph traverse(OrgId org, Query query) {
        return traverseEngine.traverse(org, query);
    }

    @Override
    public Map<RevisionHash, Revision> closure(RevisionHash root) {
        return closureEngine.closure(root);
    }

    // ---- diff --------------------------------------------------------------------------------

    @Override
    public Delta diff(RevisionHash left, RevisionHash right) {
        Revision a = revisions.get(left).orElseThrow(() -> new KernelException(
                KernelException.Reason.UNKNOWN_REVISION, "unknown revision: " + left));
        Revision b = revisions.get(right).orElseThrow(() -> new KernelException(
                KernelException.Reason.UNKNOWN_REVISION, "unknown revision: " + right));
        return diffEngine.diff(a, b);
    }

    // ---- reproduce ---------------------------------------------------------------------------

    @Override
    public ReproduceResult reproduce(OrgId org, Address.Revision target, ActorId actor) {
        Revision revision = revisions.get(target.revision()).orElseThrow(() -> new KernelException(
                KernelException.Reason.UNKNOWN_REVISION, "unknown revision: " + target.revision()));
        Map<RevisionHash, Revision> closure = closureEngine.closure(target.revision());

        Reproducer chosen = null;
        for (Reproducer r : reproducers) {
            if (r.supports(revision.kind(), revision.subtype())) {
                chosen = r;
                break;
            }
        }
        if (chosen == null) {
            return ReproduceResult.notReproducible(
                    "no reproducer for " + revision.kind().wireName() + "/" + revision.subtype());
        }

        ReproduceContext context = new ReproduceContext(org, target.node(), target.revision(), revision, closure);
        List<Revision> produced = chosen.reproduce(context);
        List<Address> observations = new ArrayList<>();
        Verb generatedFrom = new Verb("generated_from", EdgeFamily.DERIVATION);
        for (Revision observation : produced) {
            AppendResult created = append(org, new AppendCommand.CreateNode(observation), actor);
            Address.Revision obsAddress = (Address.Revision) created.address().orElseThrow();
            observations.add(obsAddress);
            append(org, new AppendCommand.AssertEdge(new EdgeKey(obsAddress, generatedFrom, target)), actor);
        }
        return new ReproduceResult(true, observations, "reproduced by " + chosen.getClass().getSimpleName());
    }

    // ---- subscribe ---------------------------------------------------------------------------

    @Override
    public Subscription subscribe(Predicate<LogEntry> pattern, SubscriptionProgram program) {
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(program, "program");
        Sub sub = new Sub(pattern, program);
        subscriptions.add(sub);
        return sub;
    }

    private void publish(LogEntry entry) {
        int depth = publishDepth.get();
        if (depth >= MAX_PUBLISH_DEPTH) {
            return; // bounded cascade: stop notifying, though the appends themselves committed
        }
        publishDepth.set(depth + 1);
        try {
            for (Sub sub : subscriptions) {
                if (sub.isActive() && sub.pattern.test(entry)) {
                    sub.program.onEntry(this, entry);
                }
            }
        } finally {
            publishDepth.set(depth);
        }
    }

    // ---- helpers / audit ---------------------------------------------------------------------

    @Override
    public Optional<Revision> revision(RevisionHash hash) {
        return revisions.get(hash);
    }

    @Override
    public boolean verifyChain(OrgId org) {
        RevisionHash expectedPrev = null;
        for (LogEntry entry : log.all(org)) {
            if (!entry.verifySelf()) {
                return false;
            }
            if (!Objects.equals(entry.prevHash(), expectedPrev)) {
                return false;
            }
            expectedPrev = entry.entryHash();
        }
        return true;
    }

    @Override
    public List<LogEntry> log(OrgId org) {
        return log.all(org);
    }

    private record Prepared(Payload payload, Optional<Address> address) {
    }

    private final class Sub implements Subscription {
        private final Predicate<LogEntry> pattern;
        private final SubscriptionProgram program;
        private volatile boolean active = true;

        private Sub(Predicate<LogEntry> pattern, SubscriptionProgram program) {
            this.pattern = pattern;
            this.program = program;
        }

        @Override
        public void close() {
            active = false;
            subscriptions.remove(this);
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
