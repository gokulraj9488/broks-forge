package com.broksforge.modules.agent.domain;

/**
 * Controls who can discover an agent. Enforcement of cross-organization
 * visibility is deferred to future modules; today every read is organization
 * scoped, and this field records intent.
 */
public enum AgentVisibility {

    /** Visible only to the owning project's members. */
    PRIVATE,

    /** Visible to all members of the owning organization. */
    ORGANIZATION,

    /** Publicly listable (reserved for a future public registry). */
    PUBLIC
}
