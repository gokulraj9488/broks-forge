package com.broksforge.explorer.watch;

import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.event.SubscriptionProgram;
import com.broksforge.kernel.core.log.LogEntry;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A passive {@link SubscriptionProgram} that records every entry it is notified of.
 *
 * <p>The simplest possible use of {@code subscribe}: a standing observer that reacts to committed facts
 * without writing any of its own. It demonstrates the read/notify half of ADR-V2-0008 — reaction is
 * subscription — and gives the demo a live tail of the log as it is written.
 */
public final class AuditTrailProgram implements SubscriptionProgram {

    private final CopyOnWriteArrayList<LogEntry> seen = new CopyOnWriteArrayList<>();

    @Override
    public void onEntry(ForgeKernel kernel, LogEntry entry) {
        seen.add(entry);
    }

    /** @return every entry this program has been notified of, in delivery order */
    public List<LogEntry> seen() {
        return List.copyOf(seen);
    }

    /** @return how many entries have been observed */
    public int count() {
        return seen.size();
    }
}
