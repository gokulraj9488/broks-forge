package com.broksforge.kernel.core;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.kernel.core.reproduce.ReproduceContext;
import com.broksforge.kernel.core.reproduce.Reproducer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared test helpers: a deterministic kernel (sequential node ids, fixed clock) and content
 * builders. Determinism keeps golden assertions and concurrency tests reproducible.
 */
final class Fixtures {

    static final OrgId ORG = OrgId.fromString("00000000-0000-0000-0000-0000000000aa");
    static final ActorId ACTOR = ActorId.of("system:test");
    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private Fixtures() {
    }

    /** A kernel with sequential (thread-safe) node-id minting, a fixed clock, and no reproducers. */
    static ForgeKernel kernel() {
        return Kernels.inMemory(sequentialMinter(), Clock.fixed(T0, ZoneOffset.UTC), List.of());
    }

    /** A kernel with the given reproducers registered. */
    static ForgeKernel kernel(Reproducer... reproducers) {
        return Kernels.inMemory(sequentialMinter(), Clock.fixed(T0, ZoneOffset.UTC), List.of(reproducers));
    }

    private static java.util.function.Supplier<NodeId> sequentialMinter() {
        AtomicLong counter = new AtomicLong(1);
        return () -> new NodeId(new UUID(0L, counter.getAndIncrement()));
    }

    static Revision prompt(String text) {
        return Revision.leaf(Kind.ARTIFACT, "prompt",
                CanonicalValue.objectBuilder().put("text", text).build());
    }

    static Revision artifact(String subtype, CanonicalValue payload, List<Ref> refs) {
        return Revision.of(Kind.ARTIFACT, subtype, payload, refs);
    }

    /** A non-AI reproducer: echoes an artifact's payload into an observation. Proves the SPI and
     *  the kernel's neutrality — it re-executes something with no AI concept whatsoever. */
    static final class EchoReproducer implements Reproducer {
        @Override
        public boolean supports(Kind kind, String subtype) {
            return kind == Kind.ARTIFACT && subtype.equals("prompt");
        }

        @Override
        public List<Revision> reproduce(ReproduceContext context) {
            CanonicalValue echoed = CanonicalValue.objectBuilder()
                    .put("echoed", context.revision().payload())
                    .put("source", context.revisionHash().toString())
                    .build();
            return List.of(Revision.leaf(Kind.OBSERVATION, "echo", echoed));
        }
    }
}
