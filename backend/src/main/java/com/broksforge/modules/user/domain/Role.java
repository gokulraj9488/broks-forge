package com.broksforge.modules.user.domain;

/**
 * Platform-wide (system) roles. Organization-scoped roles are modelled
 * separately by {@code OrganizationRole} on organization membership.
 */
public enum Role {

    /** Standard authenticated user. */
    USER,

    /** Platform administrator with elevated, cross-tenant privileges. */
    ADMIN;

    /** The Spring Security authority name, e.g. {@code ROLE_USER}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
