package com.broksforge.fxp;

import com.broksforge.kernel.core.validate.IntegrityReport;

/**
 * The result of {@code forge validate}: whether the org's hash chain verifies and the read-side integrity
 * report. Both come from the kernel — FXP runs no validation logic of its own.
 */
public record PlatformHealth(boolean chainValid, IntegrityReport integrity) {

    public boolean healthy() {
        return chainValid && integrity.clean();
    }
}
