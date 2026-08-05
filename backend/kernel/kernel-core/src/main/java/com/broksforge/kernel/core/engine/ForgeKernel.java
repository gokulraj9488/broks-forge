package com.broksforge.kernel.core.engine;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.command.AppendResult;
import com.broksforge.kernel.core.event.Subscription;
import com.broksforge.kernel.core.event.SubscriptionProgram;
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
 * The Forge Kernel — the six constitutional operations over the graph (ADR-V2-0007).
 *
 * <p>The operations are {@code append}, {@code resolve}, {@code traverse}, {@code diff},
 * {@code reproduce}, and {@code subscribe}; the remaining methods are read helpers ({@link #revision},
 * {@link #closure}, the distinguished traversal) and audit ({@link #verifyChain}, {@link #log}). The
 * kernel knows nothing of Spring, SQL, or AI: every method here is expressible entirely in memory.
 */
public interface ForgeKernel {

    // ---- 1. append ---------------------------------------------------------------------------

    /**
     * Appends one fact (Law 1: the only write). Record time is set by the kernel (Law 8).
     *
     * @param org       the organization
     * @param command   the write
     * @param actor     the signer (Law 2)
     * @param validTime when the fact was true in the world
     * @return the committed result
     * @throws KernelException if a law or precondition rejects the write
     */
    AppendResult append(OrgId org, AppendCommand command, ActorId actor, Instant validTime);

    /**
     * Appends one fact with valid time defaulted to the record time.
     *
     * @param org     the organization
     * @param command the write
     * @param actor   the signer
     * @return the committed result
     */
    AppendResult append(OrgId org, AppendCommand command, ActorId actor);

    // ---- 2. resolve --------------------------------------------------------------------------

    /**
     * @param org  the organization
     * @param name the name
     * @return the revision the name currently points at, if any
     */
    Optional<Address.Revision> resolve(OrgId org, Name name);

    /**
     * Resolves a name as of a past log position (deterministic time travel).
     *
     * @param org  the organization
     * @param name the name
     * @param asOf the position to resolve at
     * @return the revision the name pointed at as of {@code asOf}, if any
     */
    Optional<Address.Revision> resolveAt(OrgId org, Name name, LogPosition asOf);

    // ---- 3. traverse -------------------------------------------------------------------------

    /**
     * @param org   the organization
     * @param query the traversal
     * @return the reachable subgraph
     */
    Subgraph traverse(OrgId org, Query query);

    /**
     * The distinguished traversal: the composition closure of a revision (its system snapshot).
     *
     * @param root the root revision hash
     * @return the closure, root first
     */
    Map<RevisionHash, Revision> closure(RevisionHash root);

    // ---- 4. diff -----------------------------------------------------------------------------

    /**
     * @param left  the left revision hash
     * @param right the right revision hash
     * @return the structural delta between them
     * @throws KernelException if either revision is unknown
     */
    Delta diff(RevisionHash left, RevisionHash right);

    // ---- 5. reproduce ------------------------------------------------------------------------

    /**
     * Re-executes a revision through a registered reproducer, recording the resulting observations.
     *
     * @param org    the organization
     * @param target the revision to reproduce
     * @param actor  the signer of the recorded observations
     * @return the result (not-reproducible if no reproducer supports the revision)
     * @throws KernelException if the revision is unknown
     */
    ReproduceResult reproduce(OrgId org, Address.Revision target, ActorId actor);

    // ---- 6. subscribe ------------------------------------------------------------------------

    /**
     * Registers a standing program that runs on every committed entry matching {@code pattern}.
     *
     * @param pattern the predicate over committed entries
     * @param program the program to run (its outputs are ordinary appends)
     * @return a handle to cancel the subscription
     */
    Subscription subscribe(Predicate<LogEntry> pattern, SubscriptionProgram program);

    // ---- helpers / audit ---------------------------------------------------------------------

    /**
     * @param hash a revision hash
     * @return the revision content, if present
     */
    Optional<Revision> revision(RevisionHash hash);

    /**
     * Verifies the hash chain of an organization's log (tamper evidence).
     *
     * @param org the organization
     * @return true if the chain is intact from the first entry to the head
     */
    boolean verifyChain(OrgId org);

    /**
     * @param org the organization
     * @return the full log in position order (for inspection/audit)
     */
    List<LogEntry> log(OrgId org);
}
