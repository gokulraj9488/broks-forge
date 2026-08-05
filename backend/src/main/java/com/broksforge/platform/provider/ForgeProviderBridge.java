package com.broksforge.platform.provider;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.fxp.integrate.LocalModelProviderAdapter;
import com.broksforge.fxp.integrate.ModelInvocation;
import com.broksforge.fxp.integrate.ModelProviderAdapter;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.modules.model.ModelInvocationRequest;
import com.broksforge.modules.model.ModelInvocationResult;
import com.broksforge.platform.ForgePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Routes provider execution through the Platform V2 provider abstraction and records a lawful {@code Run}
 * for each invocation — through the public FXP {@link ModelProviderAdapter} and the P0 {@link ForgePlatform}
 * seam, consuming only public Platform V2 APIs.
 *
 * <p><b>P1 is dormant by construction.</b> Recording requires a lawful {@link AgentAnchorResolver}, and P1
 * ships none, so {@link #onInvocation} short-circuits and records nothing. In P2, registering a resolver
 * bean activates recording automatically — the routing hook and this bridge are unchanged.
 *
 * <p><b>Behavior-preserving:</b> {@link #onInvocation} is best-effort and never throws, so it cannot alter
 * or fail the caller's model invocation. The whole bridge exists only when {@code broksforge.platform.v2}
 * is enabled; disabling it removes the bean and reverts routing to a no-op.
 */
@Component
@ConditionalOnProperty(prefix = "broksforge.platform.v2", name = "enabled", havingValue = "true")
public class ForgeProviderBridge {

    private static final Logger log = LoggerFactory.getLogger(ForgeProviderBridge.class);

    private final ForgePlatform platform;
    private final ObjectProvider<AgentAnchorResolver> anchorResolver;
    private final ModelProviderAdapter recorder = new LocalModelProviderAdapter();

    public ForgeProviderBridge(ForgePlatform platform, ObjectProvider<AgentAnchorResolver> anchorResolver) {
        this.platform = platform;
        this.anchorResolver = anchorResolver;
    }

    /**
     * Record a lawful Run for a completed provider invocation, if (and only if) a lawful Agent anchor is
     * available. Best-effort: any failure is logged and swallowed so the caller's result is never affected.
     */
    public void onInvocation(ModelInvocationRequest request, ModelInvocationResult result) {
        try {
            if (request == null || request.organizationId() == null) {
                return;
            }
            AgentAnchorResolver resolver = anchorResolver.getIfAvailable();
            if (resolver == null) {
                return; // DORMANT (P1): no lawful anchor exists yet; activates in P2 when a resolver is registered.
            }
            OrgId org = platform.identity().toOrgId(request.organizationId());
            ForgeClient client = platform.clientFor(org, ForgePlatform.SYSTEM_ACTOR);
            Optional<KnowledgeObject> agent = resolver.resolve(request, client);
            if (agent.isEmpty()) {
                return; // resolver could not supply a lawful anchor — never fabricate one.
            }
            String status = (result != null && result.success()) ? "success" : "error";
            String output = result != null ? result.output() : null;
            recorder.record(client.studio(), agent.get(),
                    new ModelInvocation(request.model(), request.input(), output, status));
        } catch (RuntimeException e) {
            log.warn("Forge provider bridge: Run recording skipped (best-effort): {}", e.toString());
        }
    }
}
