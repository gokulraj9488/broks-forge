package com.broksforge.knowledge.validate;

import java.util.List;

/**
 * The outcome of validating a knowledge object or relationship: the issues found (most severe first).
 * {@link #valid()} is true when there are no ERROR issues. Callers validate <em>before</em> appending,
 * so the append-only kernel log never accumulates ontology-invalid facts (KN-0004).
 *
 * @param issues the issues (may be empty)
 */
public record ValidationResult(List<ValidationIssue> issues) {

    public ValidationResult {
        issues = List.copyOf(issues);
    }

    /** @return an OK result */
    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    /** @return true if there are no ERROR-severity issues */
    public boolean valid() {
        return issues.stream().noneMatch(i -> i.severity() == ValidationIssue.Severity.ERROR);
    }

    /** @return the ERROR issues only */
    public List<ValidationIssue> errors() {
        return issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).toList();
    }

    /**
     * @throws KnowledgeException if this result is invalid
     * @return this result (for chaining) if valid
     */
    public ValidationResult throwIfInvalid() {
        if (!valid()) {
            throw new KnowledgeException(this);
        }
        return this;
    }
}
