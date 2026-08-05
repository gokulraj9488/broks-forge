package com.broksforge.kernel.api;

/**
 * A position in an organization's append log — the causal clock of the Forge Graph.
 *
 * <p>Every append receives a monotonically increasing position within its organization. Log
 * position, not wall-clock time, is the axis along which history is ordered and along which any
 * query may be evaluated {@code asOf} a past state, deterministically and forever
 * (docs/v2/DOMAIN_MODEL.md §1.4).
 *
 * <p>Positions are assigned by the append engine (kernel core). {@link #ZERO} is the sentinel for
 * "before any entry"; the first real append is position 1.
 *
 * @param value the non-negative sequence number
 */
public record LogPosition(long value) implements Comparable<LogPosition> {

    /** The empty log: before any entry has been appended. */
    public static final LogPosition ZERO = new LogPosition(0);

    /**
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public LogPosition {
        if (value < 0) {
            throw new IllegalArgumentException("log position must be non-negative: " + value);
        }
    }

    /**
     * @return the next position (this one incremented by one)
     */
    public LogPosition next() {
        return new LogPosition(value + 1);
    }

    /**
     * @return true if this is the empty-log sentinel ({@link #ZERO})
     */
    public boolean isGenesis() {
        return value == 0;
    }

    @Override
    public int compareTo(LogPosition o) {
        return Long.compare(value, o.value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
