package com.broksforge.kernel.core.command;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.core.log.EdgeKey;

import java.time.Instant;

/**
 * The closed set of legal writes — the only shapes an append may take.
 *
 * <p>This sealed hierarchy is how Law 1 (append-only) and Law 10 (status is a query) are enforced
 * by the type system: there is no {@code UpdateNode}, no {@code DeleteNode}, no {@code SetStatus}.
 * The illegal operation is unrepresentable, not merely rejected. Every command becomes exactly one
 * {@link com.broksforge.kernel.core.log.LogEntry}; correction is {@link AddRevision}, withdrawal is
 * {@link RetractEdge}, and the sole mutation is {@link RepointName} (ADR-V2-0006).
 */
public sealed interface AppendCommand
        permits AppendCommand.CreateNode, AppendCommand.AddRevision,
                AppendCommand.AssertEdge, AppendCommand.RetractEdge,
                AppendCommand.RepointName, AppendCommand.Tick {

    /**
     * Create a new continuant: mint a fresh {@link NodeId} and record its first revision.
     *
     * @param revision the first revision of the new continuant
     */
    record CreateNode(Revision revision) implements AppendCommand {
        public CreateNode {
            if (revision == null) {
                throw new IllegalArgumentException("revision must not be null");
            }
        }
    }

    /**
     * Add a new revision to an existing continuant (versioning; never in-place mutation).
     *
     * @param node     the existing continuant
     * @param revision the new revision
     */
    record AddRevision(NodeId node, Revision revision) implements AppendCommand {
        public AddRevision {
            if (node == null) {
                throw new IllegalArgumentException("node must not be null");
            }
            if (revision == null) {
                throw new IllegalArgumentException("revision must not be null");
            }
        }
    }

    /**
     * Assert an extrinsic edge between two addresses.
     *
     * @param edge the edge
     */
    record AssertEdge(EdgeKey edge) implements AppendCommand {
        public AssertEdge {
            if (edge == null) {
                throw new IllegalArgumentException("edge must not be null");
            }
        }
    }

    /**
     * Withdraw a previously asserted extrinsic edge (both remain in history).
     *
     * @param edge the edge
     */
    record RetractEdge(EdgeKey edge) implements AppendCommand {
        public RetractEdge {
            if (edge == null) {
                throw new IllegalArgumentException("edge must not be null");
            }
        }
    }

    /**
     * Repoint a name at a revision, with compare-and-swap on the expected current target.
     *
     * @param name     the name
     * @param target   the revision to point at
     * @param expected the target the caller expects the name currently holds, or null if the caller
     *                 expects the name not to exist yet (first repointing)
     */
    record RepointName(Name name, Address.Revision target, Address.Revision expected)
            implements AppendCommand {
        public RepointName {
            if (name == null) {
                throw new IllegalArgumentException("name must not be null");
            }
            if (target == null) {
                throw new IllegalArgumentException("target must not be null");
            }
            // 'expected' is intentionally nullable: null means "expect the name to be new".
        }
    }

    /**
     * Emit a substrate clock tick, so time-driven subscriptions fire on a quiet log (finding B3).
     *
     * @param at the tick instant
     */
    record Tick(Instant at) implements AppendCommand {
        public Tick {
            if (at == null) {
                throw new IllegalArgumentException("tick instant must not be null");
            }
        }
    }
}
