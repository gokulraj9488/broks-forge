package com.broksforge.modules.organization.domain;

/**
 * Lifecycle state of an organization.
 */
public enum OrganizationStatus {

    /** Fully operational. */
    ACTIVE,

    /** Read-only; retained but no longer actively used. */
    ARCHIVED
}
