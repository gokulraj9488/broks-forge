package com.broksforge.explorer;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.Verb;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.command.AppendResult;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.event.Subscription;
import com.broksforge.kernel.core.event.SubscriptionProgram;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.op.Delta;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.op.Subgraph;
import com.broksforge.kernel.core.reproduce.ReproduceResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * An ergonomic, application-facing facade over a single organization's {@link ForgeKernel}.
 *
 * <p>This is the thin userspace layer the Forge Engineering Explorer builds on. It fixes an
 * {@link OrgId} and an {@link ActorId} once, returns typed {@link Handle}s instead of
 * {@code Optional<Address>} that must be unwrapped and downcast, and gives each of the six kernel
 * operations a name that reads like the engineering act it performs. It adds no capability the kernel
 * lacks — every method delegates to the public {@link ForgeKernel} API and nothing else. It is, in
 * other words, the boilerplate a real application would write on day one, gathered in one place so the
 * rest of the app never repeats it.
 */
public final class ForgeExplorer {

    private final ForgeKernel kernel;
    private final OrgId org;
    private final ActorId actor;

    private ForgeExplorer(ForgeKernel kernel, OrgId org, ActorId actor) {
        this.kernel = kernel;
        this.org = org;
        this.actor = actor;
    }

    /**
     * @param kernel the kernel to build on
     * @param org    the organization (graph boundary)
     * @param actor  the actor that signs this session's writes
     * @return a facade bound to {@code org} and {@code actor}
     */
    public static ForgeExplorer open(ForgeKernel kernel, OrgId org, ActorId actor) {
        if (kernel == null || org == null || actor == null) {
            throw new IllegalArgumentException("kernel, org, and actor must not be null");
        }
        return new ForgeExplorer(kernel, org, actor);
    }

    /** @return the underlying kernel (for capabilities this facade deliberately does not wrap) */
    public ForgeKernel kernel() {
        return kernel;
    }

    /** @return the bound organization */
    public OrgId org() {
        return org;
    }

    /** @return the bound actor */
    public ActorId actor() {
        return actor;
    }

    // ---- append (create / version) -----------------------------------------------------------

    /**
     * Creates a new continuant from a fully-formed revision (used for claims, decisions, and any
     * revision assembled by a {@code kinds} helper).
     *
     * @param revision the first revision
     * @return a handle to the created revision
     */
    public Handle create(Revision revision) {
        return handle(kernel.append(org, new AppendCommand.CreateNode(revision), actor), revision);
    }

    /**
     * Creates a new artifact continuant.
     *
     * @param subtype the artifact subtype (e.g. {@code prompt}, {@code agent})
     * @param payload the artifact content
     * @param refs    intrinsic references (e.g. composition {@code uses} refs); may be empty
     * @return a handle to the created revision
     */
    public Handle createArtifact(String subtype, CanonicalValue payload, Ref... refs) {
        return create(Revision.of(Kind.ARTIFACT, subtype, payload, List.of(refs)));
    }

    /**
     * Records an observation of reality.
     *
     * @param subtype the observation subtype (e.g. {@code metric}, {@code test-result})
     * @param payload what was observed
     * @param refs    intrinsic references; may be empty
     * @return a handle to the created revision
     */
    public Handle recordObservation(String subtype, CanonicalValue payload, Ref... refs) {
        return create(Revision.of(Kind.OBSERVATION, subtype, payload, List.of(refs)));
    }

    /**
     * Adds a new revision to an existing continuant (versioning; never in-place mutation).
     *
     * @param node     the existing continuant
     * @param revision the new revision (its kind must match the continuant's)
     * @return a handle to the new revision
     */
    public Handle addRevision(NodeId node, Revision revision) {
        return handle(kernel.append(org, new AppendCommand.AddRevision(node, revision), actor), revision);
    }

    // ---- append (edges) ----------------------------------------------------------------------

    /**
     * Asserts an extrinsic edge between two addresses.
     *
     * @param from the source address
     * @param verb the relationship verb (carries its family)
     * @param to   the target address
     */
    public void assertEdge(Address from, Verb verb, Address to) {
        kernel.append(org, new AppendCommand.AssertEdge(new EdgeKey(from, verb, to)), actor);
    }

    /**
     * Withdraws a previously asserted extrinsic edge.
     *
     * @param from the source address
     * @param verb the relationship verb
     * @param to   the target address
     */
    public void retractEdge(Address from, Verb verb, Address to) {
        kernel.append(org, new AppendCommand.RetractEdge(new EdgeKey(from, verb, to)), actor);
    }

    // ---- append (names) ----------------------------------------------------------------------

    /**
     * Points a fresh name at a revision (compare-and-swap expecting the name not to exist yet).
     *
     * @param name   the name
     * @param target the revision to point at
     * @return the name pointer address
     */
    public Address.NamePointer deploy(Name name, Handle target) {
        return repoint(name, target.address(), null);
    }

    /**
     * Repoints a name from an expected current target to a new one (deploy/promote/rollback).
     *
     * @param name     the name
     * @param target   the new target revision
     * @param expected the target the caller expects the name currently holds, or null if new
     * @return the name pointer address
     */
    public Address.NamePointer repoint(Name name, Address.Revision target, Address.Revision expected) {
        AppendResult r = kernel.append(org, new AppendCommand.RepointName(name, target, expected), actor);
        return (Address.NamePointer) r.address().orElseThrow();
    }

    /**
     * Emits a substrate clock tick (so time-driven subscriptions can fire on a quiet log).
     *
     * @param at the tick instant
     */
    public void tick(Instant at) {
        kernel.append(org, new AppendCommand.Tick(at), actor);
    }

    // ---- resolve -----------------------------------------------------------------------------

    /**
     * @param name a name
     * @return the revision the name currently points at, if any
     */
    public Optional<Address.Revision> resolve(Name name) {
        return kernel.resolve(org, name);
    }

    /**
     * @param name a name
     * @param asOf a past log position
     * @return the revision the name pointed at as of {@code asOf}, if any
     */
    public Optional<Address.Revision> resolveAt(Name name, LogPosition asOf) {
        return kernel.resolveAt(org, name, asOf);
    }

    // ---- traverse / closure ------------------------------------------------------------------

    /**
     * @param query a traversal specification
     * @return the reachable subgraph
     */
    public Subgraph traverse(Query query) {
        return kernel.traverse(org, query);
    }

    /**
     * @param start a starting address
     * @return the outward subgraph within three hops (all families)
     */
    public Subgraph neighbors(Address start) {
        return kernel.traverse(org, Query.neighbors(start));
    }

    /**
     * @param root a revision hash
     * @return its composition closure (system snapshot), root first
     */
    public Map<RevisionHash, Revision> closure(RevisionHash root) {
        return kernel.closure(root);
    }

    // ---- diff --------------------------------------------------------------------------------

    /**
     * @param left  the left revision hash
     * @param right the right revision hash
     * @return the structural delta between them
     */
    public Delta diff(RevisionHash left, RevisionHash right) {
        return kernel.diff(left, right);
    }

    // ---- reproduce ---------------------------------------------------------------------------

    /**
     * Re-executes a revision through a registered reproducer, recording the resulting observations.
     *
     * @param target the revision to reproduce
     * @return the result
     */
    public ReproduceResult reproduce(Address.Revision target) {
        return kernel.reproduce(org, target, actor);
    }

    // ---- subscribe ---------------------------------------------------------------------------

    /**
     * @param pattern a predicate over committed entries
     * @param program the standing program to run on each match
     * @return a handle to cancel the subscription
     */
    public Subscription subscribe(Predicate<LogEntry> pattern, SubscriptionProgram program) {
        return kernel.subscribe(pattern, program);
    }

    // ---- read / audit ------------------------------------------------------------------------

    /**
     * @param hash a revision hash
     * @return the revision content, if present
     */
    public Optional<Revision> revision(RevisionHash hash) {
        return kernel.revision(hash);
    }

    /** @return this organization's full log in position order */
    public List<LogEntry> log() {
        return kernel.log(org);
    }

    /** @return true if this organization's hash chain verifies (tamper evidence) */
    public boolean verifyChain() {
        return kernel.verifyChain(org);
    }

    private Handle handle(AppendResult result, Revision revision) {
        Address address = result.address().orElseThrow(
                () -> new IllegalStateException("expected a revision address from a node write"));
        return new Handle((Address.Revision) address, revision);
    }
}
