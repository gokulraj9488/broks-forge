package com.broksforge.modules.project.domain;

/**
 * Lifecycle state of a project.
 */
public enum ProjectStatus {

    /** Active and writable. */
    ACTIVE,

    /** Archived; retained for reference but no longer active. */
    ARCHIVED
}
