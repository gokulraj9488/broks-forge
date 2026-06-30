package com.broksforge.modules.user.domain;

/**
 * Lifecycle state of a user account.
 */
public enum UserStatus {

    /** Account is active and may authenticate. */
    ACTIVE,

    /** Temporarily blocked by an administrator. */
    SUSPENDED,

    /** Self-deactivated; may be reactivated. */
    DEACTIVATED;

    public boolean isLoginAllowed() {
        return this == ACTIVE;
    }
}
