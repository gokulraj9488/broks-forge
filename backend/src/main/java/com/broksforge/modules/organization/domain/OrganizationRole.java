package com.broksforge.modules.organization.domain;

/**
 * A member's role within an organization, ordered by privilege.
 */
public enum OrganizationRole {

    MEMBER(1),
    ADMIN(2),
    OWNER(3);

    private final int rank;

    OrganizationRole(int rank) {
        this.rank = rank;
    }

    /**
     * @return {@code true} if this role is at least as privileged as {@code other}
     */
    public boolean isAtLeast(OrganizationRole other) {
        return this.rank >= other.rank;
    }
}
