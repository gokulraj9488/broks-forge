package com.broksforge.fvcs.compat;

import java.util.List;

/**
 * The outcome of a compatibility check: whether snapshot B may replace snapshot A, with issues and notes.
 * A repository turns this into a {@code CompatibilityVerdict} Claim (evidenced), so no naked compatibility
 * assertion is ever stored (Law 5).
 *
 * @param compatible true if no blocking incompatibility was found
 * @param issues     blocking incompatibilities (e.g. a required object removed)
 * @param notes      non-blocking observations (changed/added objects that may need re-evaluation)
 */
public record CompatibilityResult(boolean compatible, List<String> issues, List<String> notes) {

    public CompatibilityResult {
        issues = List.copyOf(issues);
        notes = List.copyOf(notes);
    }
}
