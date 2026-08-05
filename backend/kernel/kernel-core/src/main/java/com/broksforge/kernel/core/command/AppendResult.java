package com.broksforge.kernel.core.command;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.core.log.LogEntry;

import java.util.Optional;

/**
 * The outcome of a successful append: the committed {@link LogEntry} and the primary address the
 * command produced (if any).
 *
 * <p>For {@link AppendCommand.CreateNode} and {@link AppendCommand.AddRevision} the address is the
 * new {@link Address.Revision} (from which the minted {@code NodeId} is available). For
 * {@link AppendCommand.RepointName} it is the {@link Address.NamePointer}. For edge and tick
 * commands there is no primary address.
 *
 * @param entry   the committed fact
 * @param address the primary address produced, if any
 */
public record AppendResult(LogEntry entry, Optional<Address> address) {

    /**
     * @throws IllegalArgumentException if entry or address is null (use an empty Optional, not null)
     */
    public AppendResult {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        if (address == null) {
            throw new IllegalArgumentException("address optional must not be null");
        }
    }

    /**
     * @param entry   the committed fact
     * @param address the primary address produced
     * @return a result carrying an address
     */
    public static AppendResult of(LogEntry entry, Address address) {
        return new AppendResult(entry, Optional.of(address));
    }

    /**
     * @param entry the committed fact
     * @return a result with no primary address
     */
    public static AppendResult of(LogEntry entry) {
        return new AppendResult(entry, Optional.empty());
    }
}
