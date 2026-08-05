package com.broksforge.kernel.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value-type tests for the assigned identities: {@link OrgId}, {@link NodeId}, {@link LogPosition}.
 * These are the "which thing?" and "when?" halves of the identity model (DOMAIN_MODEL §1);
 * {@link RevisionHash} (the "which state?" half) is covered separately.
 */
class IdentityTypesTest {

    @Test
    @DisplayName("NodeId round-trips and rejects invalid input")
    void nodeId() {
        UUID u = UUID.fromString("00000000-0000-0000-0000-000000000001");
        NodeId id = NodeId.of(u);
        assertEquals(id, NodeId.fromString(id.toString()));
        assertEquals(u, id.value());
        assertThrows(IllegalArgumentException.class, () -> NodeId.fromString("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> NodeId.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> new NodeId(null));
    }

    @Test
    @DisplayName("OrgId round-trips (of/fromString/toString) and rejects invalid input")
    void orgId() {
        UUID u = UUID.fromString("11111111-1111-1111-1111-111111111111");
        OrgId org = OrgId.of(u);
        assertEquals(org, OrgId.fromString(org.toString()));
        assertEquals(u, org.value());
        assertThrows(IllegalArgumentException.class, () -> OrgId.fromString("nope"));
        assertThrows(IllegalArgumentException.class, () -> OrgId.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> new OrgId(null));
    }

    @Test
    @DisplayName("distinct continuants have distinct identities")
    void distinctIdentities() {
        NodeId a = NodeId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        NodeId b = NodeId.of(UUID.fromString("00000000-0000-0000-0000-00000000000b"));
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("LogPosition orders, advances, and rejects negatives")
    void logPosition() {
        assertTrue(LogPosition.ZERO.isGenesis());
        assertEquals(new LogPosition(1), LogPosition.ZERO.next());
        assertFalse(LogPosition.ZERO.next().isGenesis());
        assertTrue(new LogPosition(1).compareTo(new LogPosition(2)) < 0);
        assertEquals(0, new LogPosition(5).compareTo(new LogPosition(5)));
        assertThrows(IllegalArgumentException.class, () -> new LogPosition(-1));
    }
}
