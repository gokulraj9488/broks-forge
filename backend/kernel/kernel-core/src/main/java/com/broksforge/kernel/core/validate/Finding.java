package com.broksforge.kernel.core.validate;

/**
 * One result from the {@link IntegrityScanner}: a severity, a stable machine code, and a
 * human-readable message locating the issue.
 *
 * @param severity how serious the finding is
 * @param code     a stable code (e.g. {@code CHAIN_BROKEN}) for programmatic handling
 * @param message  a human-readable description
 */
public record Finding(Severity severity, String code, String message) {

    /** Finding severity. */
    public enum Severity { INFO, WARNING, ERROR }

    /**
     * @param code    the code
     * @param message the message
     * @return an ERROR finding
     */
    public static Finding error(String code, String message) {
        return new Finding(Severity.ERROR, code, message);
    }

    /**
     * @param code    the code
     * @param message the message
     * @return a WARNING finding
     */
    public static Finding warning(String code, String message) {
        return new Finding(Severity.WARNING, code, message);
    }
}
