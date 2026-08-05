package com.broksforge.kernel.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Value-type tests for {@link Address} — the universal citation currency (DOMAIN_MODEL §1.3).
 * The central guarantee is exact round-tripping of every address shape, including the tricky
 * revision form (whose hash contains a colon) and the reserved {@code name} discriminator.
 */
class AddressTest {

    private static final OrgId ORG = OrgId.fromString("22222222-2222-2222-2222-222222222222");
    private static final NodeId NODE = NodeId.fromString("33333333-3333-3333-3333-333333333333");
    private static final RevisionHash REV =
            RevisionHash.of(HashAlgorithm.SHA_256, filled());

    private static byte[] filled() {
        byte[] b = new byte[32];
        java.util.Arrays.fill(b, (byte) 0xCD);
        return b;
    }

    @Test
    @DisplayName("node address round-trips")
    void nodeAddress() {
        Address a = new Address.Node(ORG, Kind.ARTIFACT, NODE);
        assertEquals("forge:" + ORG + "/artifact/" + NODE, a.toUri());
        Address parsed = Address.parse(a.toUri());
        assertInstanceOf(Address.Node.class, parsed);
        assertEquals(a, parsed);
    }

    @Test
    @DisplayName("revision address round-trips (hash contains a colon)")
    void revisionAddress() {
        Address a = new Address.Revision(ORG, Kind.CLAIM, NODE, REV);
        assertEquals("forge:" + ORG + "/claim/" + NODE + "@" + REV, a.toUri());
        Address parsed = Address.parse(a.toUri());
        assertInstanceOf(Address.Revision.class, parsed);
        assertEquals(a, parsed);
        assertEquals(REV, ((Address.Revision) parsed).revision());
    }

    @Test
    @DisplayName("name address round-trips, including multi-segment paths")
    void nameAddress() {
        Address a = new Address.NamePointer(ORG, Name.of("agents/support/current"));
        assertEquals("forge:" + ORG + "/name/agents/support/current", a.toUri());
        Address parsed = Address.parse(a.toUri());
        assertInstanceOf(Address.NamePointer.class, parsed);
        assertEquals(a, parsed);
    }

    @Test
    @DisplayName("a name whose path begins with 'name' still round-trips")
    void nameStartingWithReservedToken() {
        Address a = new Address.NamePointer(ORG, Name.of("name/foo"));
        Address parsed = Address.parse(a.toUri());
        assertEquals(a, parsed);
    }

    @Test
    @DisplayName("malformed URIs are rejected")
    void malformed() {
        assertThrows(IllegalArgumentException.class, () -> Address.parse(null));
        assertThrows(IllegalArgumentException.class, () -> Address.parse("http://x"));
        assertThrows(IllegalArgumentException.class, () -> Address.parse("forge:only-org"));
        assertThrows(IllegalArgumentException.class, () -> Address.parse("forge:" + ORG + "/notakind/" + NODE));
        assertThrows(IllegalArgumentException.class,
                () -> Address.parse("forge:" + ORG + "/name/foo@" + REV)); // names carry no revision
    }

    @Test
    @DisplayName("all three shapes round-trip over many random inputs")
    void randomRoundTrip() {
        Random rnd = new Random(99);
        for (int i = 0; i < 500; i++) {
            OrgId org = new OrgId(new UUID(rnd.nextLong(), rnd.nextLong()));
            NodeId node = new NodeId(new UUID(rnd.nextLong(), rnd.nextLong()));
            Kind kind = Kind.values()[rnd.nextInt(Kind.values().length)];
            byte[] d = new byte[32];
            rnd.nextBytes(d);
            RevisionHash rev = RevisionHash.of(HashAlgorithm.SHA_256, d);

            Address node1 = new Address.Node(org, kind, node);
            Address rev1 = new Address.Revision(org, kind, node, rev);
            Address name1 = new Address.NamePointer(org, Name.of("env/target-" + i));

            assertEquals(node1, Address.parse(node1.toUri()));
            assertEquals(rev1, Address.parse(rev1.toUri()));
            assertEquals(name1, Address.parse(name1.toUri()));
        }
    }
}
