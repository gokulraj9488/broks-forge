package com.broksforge.kernel.store.postgres;

/**
 * Wraps a low-level {@link java.sql.SQLException} as an unchecked kernel persistence failure. Storage
 * faults are not something a kernel caller can meaningfully recover from mid-operation; they surface
 * as this exception so the append transaction fails cleanly (leaving no partial state, since the
 * underlying JDBC transaction is rolled back).
 */
public class PersistenceException extends RuntimeException {

    /**
     * @param message the detail
     * @param cause   the underlying SQL failure
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
