package com.broksforge.knowledge.validate;

/**
 * Thrown when a knowledge object or relationship fails ontology validation before it can be appended.
 * It carries the full {@link ValidationResult} so callers can inspect every issue.
 */
public class KnowledgeException extends RuntimeException {

    private final transient ValidationResult result;

    /**
     * @param result the failing validation result
     */
    public KnowledgeException(ValidationResult result) {
        super("knowledge validation failed: " + result.errors());
        this.result = result;
    }

    /** @return the validation result */
    public ValidationResult result() {
        return result;
    }
}
