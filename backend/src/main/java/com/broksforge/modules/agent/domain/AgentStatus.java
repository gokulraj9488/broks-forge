package com.broksforge.modules.agent.domain;

/**
 * Lifecycle state of an agent. Distinct from health (operational reachability)
 * and from soft deletion (record retention).
 */
public enum AgentStatus {

    /** In active use. */
    ACTIVE,

    /** Retained for reference but no longer active; read-only by convention. */
    ARCHIVED
}
