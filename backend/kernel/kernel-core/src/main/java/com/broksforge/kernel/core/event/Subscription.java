package com.broksforge.kernel.core.event;

/**
 * A handle to an active subscription. Closing it cancels the subscription; closing is idempotent.
 * Implements {@link AutoCloseable} so subscriptions can be used in try-with-resources.
 */
public interface Subscription extends AutoCloseable {

    /** Cancels the subscription. Idempotent. */
    @Override
    void close();

    /** @return true if the subscription is still active */
    boolean isActive();
}
