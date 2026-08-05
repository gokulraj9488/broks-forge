package com.broksforge.explorer;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.engine.Kernels;
import com.broksforge.kernel.core.reproduce.Reproducer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test helpers for building a kernel and a {@link ForgeExplorer} against it — using only the public
 * factory {@code Kernels.inMemory}. Node ids are minted deterministically so tests are stable.
 */
final class TestKernel {

    static final OrgId ORG = OrgId.of(UUID.fromString("0000face-0000-4000-8000-000000000001"));
    static final ActorId ACTOR = ActorId.of("engineer:test");

    private TestKernel() {
    }

    static ForgeKernel kernel(Reproducer... reproducers) {
        AtomicLong seq = new AtomicLong(0);
        return Kernels.inMemory(
                () -> new NodeId(new UUID(0xABCDL, seq.incrementAndGet())),
                Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC),
                List.of(reproducers));
    }

    static ForgeExplorer explorer(Reproducer... reproducers) {
        return ForgeExplorer.open(kernel(reproducers), ORG, ACTOR);
    }

    static ForgeExplorer explorerOn(ForgeKernel kernel) {
        return ForgeExplorer.open(kernel, ORG, ACTOR);
    }
}
