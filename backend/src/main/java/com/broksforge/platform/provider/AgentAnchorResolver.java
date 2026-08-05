package com.broksforge.platform.provider;

import com.broksforge.fxp.ForgeClient;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.modules.model.ModelInvocationRequest;

import java.util.Optional;

/**
 * The P2 activation seam for lawful Run recording.
 *
 * <p>A lawful Platform V2 {@code Run} must reference an {@code executed → Agent} artifact, which requires a
 * lawful {@code Provider → Model → Prompt → Agent} graph. Those artifacts do not exist until P2 projects
 * them. Therefore <b>P1 ships no implementation of this interface</b>, and {@link ForgeProviderBridge}
 * records nothing (dormant).
 *
 * <p>In P2, a single bean implementing this interface — one that resolves (or creates) the lawful Agent
 * artifact for a given invocation — will make the recording path activate automatically, with no change to
 * the bridge or the routing hook. It must return {@link Optional#empty()} when it cannot supply a lawful
 * anchor, so the bridge stays a no-op rather than fabricating facts.
 */
public interface AgentAnchorResolver {

    /** The lawful Agent artifact to anchor a Run for this invocation, or empty to skip recording. */
    Optional<KnowledgeObject> resolve(ModelInvocationRequest request, ForgeClient client);
}
