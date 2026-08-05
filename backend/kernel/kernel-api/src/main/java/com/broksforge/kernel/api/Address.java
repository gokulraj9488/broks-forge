package com.broksforge.kernel.api;

/**
 * The universal address of something in the Forge Graph — the currency of citation and sharing.
 *
 * <p>Anything citable, shareable, subscribable, or focusable has a stable address (MANIFESTO
 * Article I; docs/v2/DOMAIN_MODEL.md §1.3). There are exactly three shapes, modelled as a sealed
 * hierarchy so that the set of legal addresses is closed and exhaustively matchable:
 *
 * <ul>
 *   <li>{@link Node} — a continuant: {@code forge:<org>/<kind>/<nodeId>}. Resolves to the
 *       continuant's current revision (via its name) at query time.</li>
 *   <li>{@link Revision} — one immutable state:
 *       {@code forge:<org>/<kind>/<nodeId>@<revisionHash>}.</li>
 *   <li>{@link NamePointer} — a name: {@code forge:<org>/name/<path>}.</li>
 * </ul>
 *
 * <p>The token {@code name} in the second path position is reserved to discriminate a
 * {@link NamePointer}; it is unambiguous because the first position is always an org UUID (never
 * the literal {@code name}) and no {@link Kind} wire name is {@code name}.
 */
public sealed interface Address permits Address.Node, Address.Revision, Address.NamePointer {

    /** The URI scheme prefix shared by every address. */
    String SCHEME = "forge:";

    /** The reserved discriminator token for name addresses. */
    String NAME_TOKEN = "name";

    /** @return the organization this address belongs to */
    OrgId org();

    /** @return the canonical URI string form of this address */
    String toUri();

    /**
     * Parses any address URI into its typed form.
     *
     * @param uri the URI, as produced by {@link #toUri()}
     * @return the parsed address
     * @throws IllegalArgumentException if the URI is null or malformed
     */
    static Address parse(String uri) {
        if (uri == null) {
            throw new IllegalArgumentException("address uri must not be null");
        }
        if (!uri.startsWith(SCHEME)) {
            throw new IllegalArgumentException("address must start with '" + SCHEME + "': " + uri);
        }
        String rest = uri.substring(SCHEME.length());

        // A revision address carries '@<algorithm>:<hex>'. Split on the first '@'.
        String left = rest;
        String revisionPart = null;
        int at = rest.indexOf('@');
        if (at >= 0) {
            left = rest.substring(0, at);
            revisionPart = rest.substring(at + 1);
        }

        String[] parts = left.split("/", -1);
        if (parts.length < 3) {
            throw new IllegalArgumentException("malformed address: " + uri);
        }
        OrgId org = OrgId.fromString(parts[0]);

        if (parts[1].equals(NAME_TOKEN)) {
            if (revisionPart != null) {
                throw new IllegalArgumentException("a name address cannot carry a revision: " + uri);
            }
            // Rejoin every segment after "<org>/name/" as the name path. Using join (rather than
            // index arithmetic) keeps a malformed input as a clean IllegalArgumentException from
            // Name.of, never a StringIndexOutOfBoundsException.
            String namePath = String.join("/", java.util.Arrays.copyOfRange(parts, 2, parts.length));
            return new NamePointer(org, Name.of(namePath));
        }

        if (parts.length != 3) {
            throw new IllegalArgumentException("malformed node/revision address: " + uri);
        }
        Kind kind = Kind.fromWireName(parts[1]);
        NodeId node = NodeId.fromString(parts[2]);
        if (revisionPart == null) {
            return new Node(org, kind, node);
        }
        return new Revision(org, kind, node, RevisionHash.parse(revisionPart));
    }

    /**
     * A continuant address: {@code forge:<org>/<kind>/<nodeId>}.
     *
     * @param org  the organization
     * @param kind the node kind
     * @param node the continuant identity
     */
    record Node(OrgId org, Kind kind, NodeId node) implements Address {
        /** @throws IllegalArgumentException if any component is null */
        public Node {
            requireNonNull(org, kind, node);
        }

        @Override
        public String toUri() {
            return SCHEME + org + "/" + kind.wireName() + "/" + node;
        }

        @Override
        public String toString() {
            return toUri();
        }
    }

    /**
     * A revision address: {@code forge:<org>/<kind>/<nodeId>@<revisionHash>}.
     *
     * @param org      the organization
     * @param kind     the node kind
     * @param node     the continuant identity
     * @param revision the immutable revision
     */
    record Revision(OrgId org, Kind kind, NodeId node, RevisionHash revision) implements Address {
        /** @throws IllegalArgumentException if any component is null */
        public Revision {
            requireNonNull(org, kind, node);
            if (revision == null) {
                throw new IllegalArgumentException("revision must not be null");
            }
        }

        @Override
        public String toUri() {
            return SCHEME + org + "/" + kind.wireName() + "/" + node + "@" + revision;
        }

        @Override
        public String toString() {
            return toUri();
        }
    }

    /**
     * A name address: {@code forge:<org>/name/<path>}.
     *
     * @param org  the organization
     * @param name the name path
     */
    record NamePointer(OrgId org, Name name) implements Address {
        /** @throws IllegalArgumentException if any component is null */
        public NamePointer {
            if (org == null) {
                throw new IllegalArgumentException("org must not be null");
            }
            if (name == null) {
                throw new IllegalArgumentException("name must not be null");
            }
        }

        @Override
        public String toUri() {
            return SCHEME + org + "/" + NAME_TOKEN + "/" + name.path();
        }

        @Override
        public String toString() {
            return toUri();
        }
    }

    private static void requireNonNull(OrgId org, Kind kind, NodeId node) {
        if (org == null) {
            throw new IllegalArgumentException("org must not be null");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }
    }
}
