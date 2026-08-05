package com.broksforge.kernel.core.event;

import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.LogEntry;

/**
 * A standing program bound to a subscription (ADR-V2-0008). When a committed {@link LogEntry} matches
 * the subscription's pattern, its program runs; anything the program does is done through the kernel,
 * so its outputs are ordinary appends signed by its own actor (Law 9 — no privileged writer). This is
 * the entire autonomy mechanism: reaction is subscription, and subscription outputs are facts.
 */
@FunctionalInterface
public interface SubscriptionProgram {

    /**
     * @param kernel the kernel, through which the program may append its outputs
     * @param entry  the committed entry that matched the subscription's pattern
     */
    void onEntry(ForgeKernel kernel, LogEntry entry);
}
