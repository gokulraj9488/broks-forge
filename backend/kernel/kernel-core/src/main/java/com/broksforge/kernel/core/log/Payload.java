package com.broksforge.kernel.core.log;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;

import java.time.Instant;

/**
 * The content of a single append — the payload a {@link LogEntry} carries.
 *
 * <p>The append log is the sole source of truth (ADR-V2-0001); every projection is rebuilt by
 * folding these payloads. Therefore a payload carries <b>everything needed to rebuild</b>: a
 * {@link NodePut} holds the full {@link Revision}, not merely its hash, so the revision store is
 * regenerable from the log alone.
 *
 * <p>The set of payloads is closed (a sealed hierarchy): these are the only kinds of fact the
 * kernel records. Each payload can render itself to a {@link CanonicalValue} for the entry hash
 * chain (§ {@link LogEntry}).
 */
public sealed interface Payload
        permits Payload.NodePut, Payload.EdgeAsserted, Payload.EdgeRetracted,
                Payload.NameRepointed, Payload.ClockTick {

    /** @return the canonical representation of this payload, used in the entry hash chain */
    CanonicalValue toCanonical();

    /** A new revision of a continuant (minted or existing). Carries full content for rebuild. */
    record NodePut(NodeId node, Revision revision) implements Payload {
        public NodePut {
            if (node == null) {
                throw new IllegalArgumentException("node must not be null");
            }
            if (revision == null) {
                throw new IllegalArgumentException("revision must not be null");
            }
        }

        @Override
        public CanonicalValue toCanonical() {
            return CanonicalValue.objectBuilder()
                    .put("type", "node-put")
                    .put("node", node.toString())
                    .put("revision", revision.hash().toString())
                    .build();
        }
    }

    /** An extrinsic edge asserted between two addresses. */
    record EdgeAsserted(EdgeKey edge) implements Payload {
        public EdgeAsserted {
            if (edge == null) {
                throw new IllegalArgumentException("edge must not be null");
            }
        }

        @Override
        public CanonicalValue toCanonical() {
            return edgeCanonical("edge-asserted", edge);
        }
    }

    /** Withdrawal of a previously asserted edge (the edge and its retraction both remain history). */
    record EdgeRetracted(EdgeKey edge) implements Payload {
        public EdgeRetracted {
            if (edge == null) {
                throw new IllegalArgumentException("edge must not be null");
            }
        }

        @Override
        public CanonicalValue toCanonical() {
            return edgeCanonical("edge-retracted", edge);
        }
    }

    /** A repointing of a name (the only mutation; itself an immutable logged fact). */
    record NameRepointed(Name name, Address.Revision from, Address.Revision to) implements Payload {
        public NameRepointed {
            if (name == null) {
                throw new IllegalArgumentException("name must not be null");
            }
            if (to == null) {
                throw new IllegalArgumentException("name target must not be null");
            }
            // 'from' may be null: the first repointing of a fresh name.
        }

        @Override
        public CanonicalValue toCanonical() {
            return CanonicalValue.objectBuilder()
                    .put("type", "name-repointed")
                    .put("name", name.path())
                    .put("from", from == null ? CanonicalValue.NULL : CanonicalValue.of(from.toUri()))
                    .put("to", CanonicalValue.of(to.toUri()))
                    .build();
        }
    }

    /** A tick of the substrate clock — the one observation the kernel authors on its own behalf. */
    record ClockTick(Instant at) implements Payload {
        public ClockTick {
            if (at == null) {
                throw new IllegalArgumentException("tick instant must not be null");
            }
        }

        @Override
        public CanonicalValue toCanonical() {
            return CanonicalValue.objectBuilder()
                    .put("type", "clock-tick")
                    .put("at", at.toString())
                    .build();
        }
    }

    private static CanonicalValue edgeCanonical(String type, EdgeKey edge) {
        return CanonicalValue.objectBuilder()
                .put("type", type)
                .put("from", edge.from().toUri())
                .put("verb", edge.verb().name())
                .put("family", edge.verb().family().wireName())
                .put("to", edge.to().toUri())
                .build();
    }
}
