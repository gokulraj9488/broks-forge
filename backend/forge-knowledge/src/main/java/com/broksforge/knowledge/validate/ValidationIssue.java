package com.broksforge.knowledge.validate;

/**
 * One problem found while validating a knowledge object or relationship against the ontology.
 *
 * @param severity how serious the issue is
 * @param code     a stable machine code (e.g. {@code MISSING_FIELD}, {@code ENDPOINT_TYPE})
 * @param message  a human-readable description
 */
public record ValidationIssue(Severity severity, String code, String message) {

    /** Issue severity. */
    public enum Severity { WARNING, ERROR }

    /** @param code code @param message message @return an ERROR issue */
    public static ValidationIssue error(String code, String message) {
        return new ValidationIssue(Severity.ERROR, code, message);
    }

    /** @param code code @param message message @return a WARNING issue */
    public static ValidationIssue warning(String code, String message) {
        return new ValidationIssue(Severity.WARNING, code, message);
    }
}
