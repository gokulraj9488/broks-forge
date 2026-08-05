package com.broksforge.platform;

import com.broksforge.fvcs.repo.Repository;
import com.broksforge.fxp.ForgeClient;
import com.broksforge.fxp.PlatformHealth;
import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.core.engine.ForgeKernel;

/**
 * The single seam through which the application reaches Platform V2. It holds the initialized
 * {@link ForgeKernel} and the {@link ForgeIdentityBridge}, and can hand out a per-org/-actor
 * {@code Repository}/{@code ForgeClient} — all through public platform APIs only.
 *
 * <p><b>P0:</b> this facade is <b>dormant</b>. Only {@link #health(OrgId)} is exercised (by the startup
 * initializer); {@link #repositoryFor}/{@link #clientFor} exist as the seam for P1 and are called by no
 * controller or request path. No business logic lives here.
 */
public final class ForgePlatform {

    /** Actor used for platform-internal, non-user operations such as the startup health probe. */
    public static final ActorId SYSTEM_ACTOR = ActorId.of("system:forge-platform");

    private final ForgeKernel kernel;
    private final ForgeIdentityBridge identity;

    public ForgePlatform(ForgeKernel kernel, ForgeIdentityBridge identity) {
        this.kernel = kernel;
        this.identity = identity;
    }

    public ForgeKernel kernel() {
        return kernel;
    }

    public ForgeIdentityBridge identity() {
        return identity;
    }

    /** Open an FVCS repository scoped to an org/actor (public API). Seam for P1; unused in P0. */
    public Repository repositoryFor(OrgId org, ActorId actor) {
        return Repository.open(kernel, org, actor);
    }

    /** Open the conceptual client scoped to an org/actor (public API). Seam for P1; unused in P0. */
    public ForgeClient clientFor(OrgId org, ActorId actor) {
        return ForgeClient.open(repositoryFor(org, actor), actor);
    }

    /** Verify chain + integrity for an org (used by the startup health check). */
    public PlatformHealth health(OrgId org) {
        return clientFor(org, SYSTEM_ACTOR).validate();
    }
}
