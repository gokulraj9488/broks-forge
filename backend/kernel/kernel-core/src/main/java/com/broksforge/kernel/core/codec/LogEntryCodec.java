package com.broksforge.kernel.core.codec;

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
import com.broksforge.kernel.api.canonical.CanonicalParser;
import com.broksforge.kernel.api.canonical.CanonicalSerializer;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full-fidelity serialization of a {@link LogEntry} to and from canonical bytes — the codec a durable
 * backend uses to store the log (the sole source of truth) and rebuild it exactly.
 *
 * <p>Unlike {@link Payload#toCanonical()} (which is lossy on purpose — it stores a revision's hash for
 * the entry chain), this codec stores the <b>full</b> content of every payload, including complete
 * {@link Revision}s, so projections can be regenerated from the persisted log alone. The round-trip
 * contract is {@code decode(encode(e)).equals(e)} for every entry.
 */
public final class LogEntryCodec {

    private LogEntryCodec() {
    }

    /**
     * @param entry the entry
     * @return its full canonical byte encoding
     */
    public static byte[] encode(LogEntry entry) {
        return CanonicalSerializer.toBytes(encodeEntry(entry));
    }

    /**
     * @param bytes canonical bytes produced by {@link #encode}
     * @return the reconstructed entry (equal to the original)
     */
    public static LogEntry decode(byte[] bytes) {
        return decodeEntry(obj(CanonicalParser.parse(bytes)));
    }

    // ---- encode ------------------------------------------------------------------------------

    private static CanonicalValue encodeEntry(LogEntry e) {
        return CanonicalValue.objectBuilder()
                .put("org", e.org().toString())
                .put("position", e.position().value())
                .put("prev", e.prevHash() == null ? CanonicalValue.NULL : CanonicalValue.of(e.prevHash().toString()))
                .put("entryHash", e.entryHash().toString())
                .put("provenance", CanonicalValue.objectBuilder()
                        .put("actor", e.provenance().actor().value())
                        .put("validTime", e.provenance().validTime().toString())
                        .put("recordTime", e.provenance().recordTime().toString())
                        .build())
                .put("payload", encodePayload(e.payload()))
                .build();
    }

    private static CanonicalValue encodePayload(Payload payload) {
        return switch (payload) {
            case Payload.NodePut np -> CanonicalValue.objectBuilder()
                    .put("type", "node-put")
                    .put("node", np.node().toString())
                    .put("revision", encodeRevision(np.revision()))
                    .build();
            case Payload.EdgeAsserted ea -> encodeEdge("edge-asserted", ea.edge());
            case Payload.EdgeRetracted er -> encodeEdge("edge-retracted", er.edge());
            case Payload.NameRepointed nr -> CanonicalValue.objectBuilder()
                    .put("type", "name-repointed")
                    .put("name", nr.name().path())
                    .put("from", nr.from() == null ? CanonicalValue.NULL : CanonicalValue.of(nr.from().toUri()))
                    .put("to", nr.to().toUri())
                    .build();
            case Payload.ClockTick ct -> CanonicalValue.objectBuilder()
                    .put("type", "clock-tick")
                    .put("at", ct.at().toString())
                    .build();
        };
    }

    private static CanonicalValue encodeEdge(String type, EdgeKey edge) {
        return CanonicalValue.objectBuilder()
                .put("type", type)
                .put("from", edge.from().toUri())
                .put("verb", edge.verb().name())
                .put("family", edge.verb().family().wireName())
                .put("to", edge.to().toUri())
                .build();
    }

    private static CanonicalValue encodeRevision(Revision r) {
        List<CanonicalValue> refs = new ArrayList<>();
        for (Ref ref : r.refs()) {
            refs.add(CanonicalValue.objectBuilder()
                    .put("verb", ref.verb().name())
                    .put("family", ref.verb().family().wireName())
                    .put("target", ref.target().toString())
                    .build());
        }
        return CanonicalValue.objectBuilder()
                .put("kind", r.kind().wireName())
                .put("subtype", r.subtype())
                .put("payload", r.payload())
                .put("refs", CanonicalValue.array(refs))
                .build();
    }

    // ---- decode ------------------------------------------------------------------------------

    private static LogEntry decodeEntry(CanonicalValue.Obj o) {
        OrgId org = OrgId.fromString(str(o, "org"));
        LogPosition position = new LogPosition(lng(o, "position"));
        CanonicalValue prev = field(o, "prev");
        RevisionHash prevHash = prev instanceof CanonicalValue.Null ? null : RevisionHash.parse(asStr(prev));
        RevisionHash entryHash = RevisionHash.parse(str(o, "entryHash"));
        CanonicalValue.Obj prov = obj(field(o, "provenance"));
        Provenance provenance = new Provenance(
                ActorId.of(str(prov, "actor")),
                Instant.parse(str(prov, "validTime")),
                Instant.parse(str(prov, "recordTime")));
        Payload payload = decodePayload(obj(field(o, "payload")));
        return new LogEntry(org, position, prevHash, provenance, payload, entryHash);
    }

    private static Payload decodePayload(CanonicalValue.Obj o) {
        String type = str(o, "type");
        return switch (type) {
            case "node-put" -> new Payload.NodePut(
                    NodeId.fromString(str(o, "node")), decodeRevision(obj(field(o, "revision"))));
            case "edge-asserted" -> new Payload.EdgeAsserted(decodeEdge(o));
            case "edge-retracted" -> new Payload.EdgeRetracted(decodeEdge(o));
            case "name-repointed" -> {
                CanonicalValue from = field(o, "from");
                yield new Payload.NameRepointed(
                        Name.of(str(o, "name")),
                        from instanceof CanonicalValue.Null ? null : revisionAddress(asStr(from)),
                        revisionAddress(str(o, "to")));
            }
            case "clock-tick" -> new Payload.ClockTick(Instant.parse(str(o, "at")));
            default -> throw new IllegalArgumentException("unknown payload type: " + type);
        };
    }

    private static EdgeKey decodeEdge(CanonicalValue.Obj o) {
        return new EdgeKey(
                Address.parse(str(o, "from")),
                new Verb(str(o, "verb"), EdgeFamily.fromWireName(str(o, "family"))),
                Address.parse(str(o, "to")));
    }

    private static Revision decodeRevision(CanonicalValue.Obj o) {
        List<Ref> refs = new ArrayList<>();
        for (CanonicalValue item : ((CanonicalValue.Arr) field(o, "refs")).items()) {
            CanonicalValue.Obj ro = obj(item);
            refs.add(Ref.of(
                    new Verb(str(ro, "verb"), EdgeFamily.fromWireName(str(ro, "family"))),
                    RevisionHash.parse(str(ro, "target"))));
        }
        return Revision.of(
                Kind.fromWireName(str(o, "kind")),
                str(o, "subtype"),
                field(o, "payload"),
                refs);
    }

    private static Address.Revision revisionAddress(String uri) {
        if (Address.parse(uri) instanceof Address.Revision r) {
            return r;
        }
        throw new IllegalArgumentException("expected a revision address: " + uri);
    }

    // ---- field helpers -----------------------------------------------------------------------

    private static CanonicalValue.Obj obj(CanonicalValue v) {
        if (v instanceof CanonicalValue.Obj o) {
            return o;
        }
        throw new IllegalArgumentException("expected an object, got " + v);
    }

    private static CanonicalValue field(CanonicalValue.Obj o, String key) {
        Map<String, CanonicalValue> entries = o.entries();
        CanonicalValue v = entries.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing field: " + key);
        }
        return v;
    }

    private static String asStr(CanonicalValue v) {
        if (v instanceof CanonicalValue.Str s) {
            return s.value();
        }
        throw new IllegalArgumentException("expected a string, got " + v);
    }

    private static String str(CanonicalValue.Obj o, String key) {
        return asStr(field(o, key));
    }

    private static long lng(CanonicalValue.Obj o, String key) {
        if (field(o, key) instanceof CanonicalValue.Num n) {
            return n.value().longValueExact();
        }
        throw new IllegalArgumentException("expected a number: " + key);
    }
}
