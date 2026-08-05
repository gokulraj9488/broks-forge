package com.broksforge.kernel.core;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.event.Subscription;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.op.Query;
import com.broksforge.kernel.core.reproduce.ReproduceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** subscribe (op) — the log as event bus — and reproduce (op) via the SPI. */
class EventReproduceTest {

    @Test
    @DisplayName("a subscription fires on matching entries and stops after cancel")
    void subscribeAndCancel() {
        ForgeKernel k = Fixtures.kernel();
        AtomicInteger fires = new AtomicInteger();
        Subscription sub = k.subscribe(e -> e.payload() instanceof Payload.NodePut,
                (kernel, entry) -> fires.incrementAndGet());

        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("a")), Fixtures.ACTOR);
        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("b")), Fixtures.ACTOR);
        assertEquals(2, fires.get());

        sub.close();
        assertFalse(sub.isActive());
        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("c")), Fixtures.ACTOR);
        assertEquals(2, fires.get());
    }

    @Test
    @DisplayName("clock ticks are ordinary facts a subscription can match")
    void clockTick() {
        ForgeKernel k = Fixtures.kernel();
        AtomicInteger ticks = new AtomicInteger();
        k.subscribe(e -> e.payload() instanceof Payload.ClockTick, (kernel, entry) -> ticks.incrementAndGet());
        k.append(Fixtures.ORG, new AppendCommand.Tick(Instant.parse("2026-02-02T00:00:00Z")), Fixtures.ACTOR);
        assertEquals(1, ticks.get());
    }

    @Test
    @DisplayName("a subscription program's appends are ordinary facts (Law 9) and cascades terminate")
    void cascade() {
        ForgeKernel k = Fixtures.kernel();
        // On every new 'prompt' artifact, append a 'note' observation. It must NOT react to its own
        // note (different subtype), so the cascade terminates after one hop.
        k.subscribe(
                e -> e.payload() instanceof Payload.NodePut np
                        && np.revision().kind() == Kind.ARTIFACT
                        && np.revision().subtype().equals("prompt"),
                (kernel, entry) -> kernel.append(Fixtures.ORG, new AppendCommand.CreateNode(
                        com.broksforge.kernel.api.Revision.leaf(Kind.OBSERVATION, "note",
                                CanonicalValue.of("seen"))), Fixtures.ACTOR));

        k.append(Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("hi")), Fixtures.ACTOR);
        // Two facts: the prompt, and the note the program appended.
        assertEquals(2, k.log(Fixtures.ORG).size());
    }

    @Test
    @DisplayName("reproduce runs the SPI and links observations back to the source")
    void reproduce() {
        ForgeKernel k = Fixtures.kernel(new Fixtures.EchoReproducer());
        Address.Revision source = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(Fixtures.prompt("echo me")), Fixtures.ACTOR)
                .address().orElseThrow();

        ReproduceResult result = k.reproduce(Fixtures.ORG, source, Fixtures.ACTOR);
        assertTrue(result.reproduced());
        assertEquals(1, result.observations().size());

        // A generated_from (derivation) edge links the observation to the source.
        var incoming = k.traverse(Fixtures.ORG, new Query(source,
                java.util.Set.of(EdgeFamily.DERIVATION), Query.Direction.IN, 2));
        assertEquals(1, incoming.edges().size());
        assertEquals("generated_from", incoming.edges().get(0).verb().name());
    }

    @Test
    @DisplayName("reproduce is not-reproducible when no reproducer supports the revision")
    void notReproducible() {
        ForgeKernel k = Fixtures.kernel(new Fixtures.EchoReproducer());
        Address.Revision unsupported = (Address.Revision) k.append(
                Fixtures.ORG, new AppendCommand.CreateNode(
                        com.broksforge.kernel.api.Revision.leaf(Kind.ARTIFACT, "dataset", CanonicalValue.NULL)),
                Fixtures.ACTOR).address().orElseThrow();
        ReproduceResult result = k.reproduce(Fixtures.ORG, unsupported, Fixtures.ACTOR);
        assertFalse(result.reproduced());
        assertTrue(result.observations().isEmpty());
    }
}
