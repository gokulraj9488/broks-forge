package com.broksforge.fxp.integrate;

import com.broksforge.fxp.studio.StudioService;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.knowledge.graph.KnowledgeObject;
import com.broksforge.knowledge.ontology.ObjectTypes;

/**
 * Reference source-control adapter for GitHub/GitLab (and any Git host). It maps a prompt file committed
 * upstream to a Forge {@code Prompt} artifact via the public Studio API. No network is required to prove the
 * boundary: the adapter's only capability is translation into platform facts. GitHub- and GitLab-specific
 * transports are thin wrappers that construct {@link ExternalCommit}s and delegate here.
 */
public final class GitSourceControlAdapter implements SourceControlAdapter {

    @Override
    public KnowledgeObject ingest(StudioService studio, ExternalCommit commit) {
        CanonicalValue payload = CanonicalValue.objectBuilder()
                .put("text", commit.content())
                .build();
        // The platform records the content; the external sha/message travel as provenance context only.
        return studio.create(ObjectTypes.PROMPT, payload);
    }
}
