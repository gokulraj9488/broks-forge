package com.broksforge.platform;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.security.SecurityUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * Bridges the application's identity/tenancy model onto the platform's identifiers.
 *
 * <p>The application remains the system of record for identity; the kernel simply needs an {@link OrgId}
 * (per-org log = hard tenant isolation) and an {@link ActorId} (attribution on every write). Because both
 * the application's organization id and user id are already UUIDs, the mapping is direct and deterministic.
 *
 * <p><b>P0:</b> this bridge is defined and unit-testable but <b>dormant</b> — no controller or request path
 * calls it yet. It is the seam later phases use to attribute kernel writes to the acting user/org.
 */
public final class ForgeIdentityBridge {

    private static final String USER_ACTOR_PREFIX = "user:";

    /** The application organization id becomes the kernel org id directly. */
    public OrgId toOrgId(UUID organizationId) {
        return OrgId.of(organizationId);
    }

    /** The authenticated user id becomes a stable, attributable kernel actor id. */
    public ActorId toActorId(UUID userId) {
        return ActorId.of(USER_ACTOR_PREFIX + userId);
    }

    /** The currently authenticated principal as an actor, if any (empty when unauthenticated). */
    public Optional<ActorId> currentActor() {
        return SecurityUtils.getCurrentUserId().map(this::toActorId);
    }
}
