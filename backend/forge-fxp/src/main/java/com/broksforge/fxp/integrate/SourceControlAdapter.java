package com.broksforge.fxp.integrate;

import com.broksforge.fxp.studio.StudioService;
import com.broksforge.knowledge.graph.KnowledgeObject;

/**
 * Adapter SPI for source-control systems (GitHub, GitLab, and any Git host). An adapter <b>translates</b>
 * an external commit into lawful Forge facts through the public write API — it never gives the platform a
 * dependency on the external system. The dependency is strictly one-way: adapter → platform.
 */
public interface SourceControlAdapter {

    /** Ingest an external commit, recording it as a Forge artifact (revision) via Studio. */
    KnowledgeObject ingest(StudioService studio, ExternalCommit commit);
}
