package com.broksforge.explorer.render;

import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;

/**
 * Renders committed {@link LogEntry} facts into human-readable one-liners for the audit view.
 *
 * <p>Reading the log means pattern-matching the sealed {@link Payload} hierarchy — a clean exhaustive
 * switch, once you have discovered that {@code Payload} and its variants live in the
 * {@code com.broksforge.kernel.core.log} package. That an application must import a package named
 * {@code ...core.log} to read its own history is recorded in the usability report as a naming friction
 * point (the package reads as an internal, but its types are part of the public read surface).
 */
public final class Payloads {

    private Payloads() {
    }

    /**
     * @param entry a committed log entry
     * @return a one-line description of the fact it records
     */
    public static String describe(LogEntry entry) {
        String who = entry.provenance().actor().value();
        String where = "pos " + pad(entry.position().value());
        return where + "  " + body(entry.payload()) + "   —  " + who;
    }

    private static String body(Payload payload) {
        return switch (payload) {
            case Payload.NodePut np -> "put    " + np.revision().kind().wireName() + "/" + np.revision().subtype()
                    + " " + shortHash(np.revision().hash()) + " node=" + shortId(np.node().toString());
            case Payload.EdgeAsserted ea -> "edge+  " + edge(ea.edge());
            case Payload.EdgeRetracted er -> "edge-  " + edge(er.edge());
            case Payload.NameRepointed nr -> "name→  " + nr.name().path()
                    + " := " + shortHash(nr.to().revision())
                    + (nr.from() == null ? " (new)" : " (was " + shortHash(nr.from().revision()) + ")");
            case Payload.ClockTick ct -> "tick   " + ct.at();
        };
    }

    private static String edge(EdgeKey edge) {
        return edge.verb().name() + " [" + edge.verb().family().wireName() + "] "
                + shortAddress(edge.from().toUri()) + " → " + shortAddress(edge.to().toUri());
    }

    /**
     * @param hash a revision hash
     * @return the algorithm tag plus the first 10 hex chars, e.g. {@code sha-256:e3b0c44298…}
     */
    public static String shortHash(RevisionHash hash) {
        String hex = hash.hex();
        return hash.algorithm().wireName() + ":" + hex.substring(0, Math.min(10, hex.length())) + "…";
    }

    private static String shortId(String uuid) {
        return uuid.length() >= 8 ? uuid.substring(0, 8) + "…" : uuid;
    }

    private static String shortAddress(String uri) {
        int at = uri.indexOf('@');
        if (at < 0) {
            return uri;
        }
        String head = uri.substring(0, at);
        String tail = uri.substring(at + 1);
        return head + "@" + tail.substring(0, Math.min(14, tail.length())) + "…";
    }

    private static String pad(long value) {
        return String.format("%3d", value);
    }
}
