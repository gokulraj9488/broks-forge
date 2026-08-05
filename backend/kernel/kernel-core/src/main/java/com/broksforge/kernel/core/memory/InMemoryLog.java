package com.broksforge.kernel.core.memory;

import com.broksforge.kernel.api.LogPosition;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.store.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link Log}. The append log is the sole source of truth; this backend holds it as a
 * per-org list guarded by a per-org monitor, which is the total-order serialization point. Reads
 * return copies so callers never see a concurrently mutated list.
 */
public final class InMemoryLog implements Log {

    private final Map<OrgId, List<LogEntry>> entries = new ConcurrentHashMap<>();
    private final Map<OrgId, Object> locks = new ConcurrentHashMap<>();

    private Object lockFor(OrgId org) {
        return locks.computeIfAbsent(org, o -> new Object());
    }

    @Override
    public LogEntry append(OrgId org, Sealer sealer) {
        synchronized (lockFor(org)) {
            List<LogEntry> list = entries.computeIfAbsent(org, o -> new ArrayList<>());
            LogPosition position = new LogPosition(list.size() + 1L);
            RevisionHash prevHash = list.isEmpty() ? null : list.get(list.size() - 1).entryHash();
            LogEntry entry = sealer.seal(position, prevHash);
            if (!entry.position().equals(position)) {
                throw new IllegalStateException("sealer returned wrong position");
            }
            boolean prevMatches = (prevHash == null && entry.prevHash() == null)
                    || (prevHash != null && prevHash.equals(entry.prevHash()));
            if (!prevMatches) {
                throw new IllegalStateException("sealer returned wrong previous hash");
            }
            list.add(entry);
            return entry;
        }
    }

    @Override
    public LogPosition head(OrgId org) {
        synchronized (lockFor(org)) {
            List<LogEntry> list = entries.get(org);
            return (list == null || list.isEmpty()) ? LogPosition.ZERO : new LogPosition(list.size());
        }
    }

    @Override
    public List<LogEntry> read(OrgId org, LogPosition fromExclusive, LogPosition toInclusive) {
        synchronized (lockFor(org)) {
            List<LogEntry> list = entries.get(org);
            if (list == null) {
                return List.of();
            }
            List<LogEntry> out = new ArrayList<>();
            for (LogEntry e : list) {
                long p = e.position().value();
                if (p > fromExclusive.value() && p <= toInclusive.value()) {
                    out.add(e);
                }
            }
            return out;
        }
    }

    @Override
    public List<LogEntry> all(OrgId org) {
        synchronized (lockFor(org)) {
            List<LogEntry> list = entries.get(org);
            return list == null ? List.of() : List.copyOf(list);
        }
    }
}
