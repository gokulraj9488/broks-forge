package com.broksforge.kernel.core;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.api.HashAlgorithm;
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
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.codec.LogEntryCodec;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for {@link LogEntryCodec} — the durable-log codec. Full fidelity is what makes a
 * persistent backend able to rebuild every projection from the stored log alone (ADR-V2-0001).
 */
class LogEntryCodecTest {

    private static final OrgId ORG = OrgId.fromString("00000000-0000-0000-0000-0000000000dd");
    private static final ActorId ACTOR = ActorId.of("system:codec");
    private static final Instant T = Instant.parse("2026-03-03T03:03:03Z");

    private static LogEntry seal(RevisionHash prev, long pos, Payload payload) {
        return LogEntry.seal(ORG, new LogPosition(pos), prev, new Provenance(ACTOR, T, T), payload);
    }

    private static void assertRoundTrips(LogEntry e) {
        LogEntry back = LogEntryCodec.decode(LogEntryCodec.encode(e));
        assertEquals(e, back);
        assertTrue(back.verifySelf(), "decoded entry must still verify its own hash");
    }

    private static Address.Revision rev(int fill) {
        byte[] b = new byte[32];
        java.util.Arrays.fill(b, (byte) fill);
        return new Address.Revision(ORG, Kind.ARTIFACT, new NodeId(new UUID(0, fill)),
                RevisionHash.of(HashAlgorithm.SHA_256, b));
    }

    @Test
    @DisplayName("node-put with intrinsic refs round-trips")
    void nodePut() {
        RevisionHash target = rev(1).revision();
        Revision revision = Revision.of(Kind.ARTIFACT, "agent",
                CanonicalValue.objectBuilder().put("name", "support").put("temp", CanonicalValue.of(
                        new java.math.BigDecimal("0.20"))).build(),
                List.of(Ref.of(new Verb("uses", EdgeFamily.COMPOSITION), target)));
        assertRoundTrips(seal(null, 1, new Payload.NodePut(new NodeId(new UUID(0, 9)), revision)));
    }

    @Test
    @DisplayName("edge asserted and retracted round-trip")
    void edges() {
        EdgeKey edge = new EdgeKey(rev(1), new Verb("caused", EdgeFamily.CAUSALITY), rev(2));
        assertRoundTrips(seal(rev(7).revision(), 2, new Payload.EdgeAsserted(edge)));
        assertRoundTrips(seal(rev(7).revision(), 3, new Payload.EdgeRetracted(edge)));
    }

    @Test
    @DisplayName("name repointed round-trips with and without a previous target")
    void nameRepointed() {
        assertRoundTrips(seal(null, 1, new Payload.NameRepointed(Name.of("prod"), null, rev(3))));
        assertRoundTrips(seal(rev(1).revision(), 2,
                new Payload.NameRepointed(Name.of("agents/support/current"), rev(3), rev(4))));
    }

    @Test
    @DisplayName("clock tick round-trips")
    void clockTick() {
        assertRoundTrips(seal(null, 1, new Payload.ClockTick(Instant.parse("2026-04-04T04:04:04Z"))));
    }
}
