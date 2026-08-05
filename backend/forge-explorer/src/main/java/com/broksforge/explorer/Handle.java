package com.broksforge.explorer;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.NodeId;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;

/**
 * A convenience handle to a node revision the application just wrote or read.
 *
 * <p>It exists to absorb a small but pervasive piece of raw-kernel boilerplate: every successful
 * {@code append} of a {@code CreateNode}/{@code AddRevision} returns an
 * {@code AppendResult} whose {@code address()} is an {@code Optional<Address>} that the caller must
 * unwrap <em>and</em> downcast to {@code Address.Revision} before it is useful. Bundling the minted
 * {@link NodeId}, the typed {@link Address.Revision}, and the {@link Revision} content together means
 * the cast and the unwrap happen exactly once, inside {@link ForgeExplorer}, instead of at every call
 * site.
 *
 * @param address  the revision address produced by the write
 * @param revision the revision content
 */
public record Handle(Address.Revision address, Revision revision) {

    public Handle {
        if (address == null) {
            throw new IllegalArgumentException("address must not be null");
        }
        if (revision == null) {
            throw new IllegalArgumentException("revision must not be null");
        }
    }

    /** @return the continuant identity */
    public NodeId node() {
        return address.node();
    }

    /** @return the content-addressed revision hash */
    public RevisionHash hash() {
        return address.revision();
    }

    /** @return the kind of this node */
    public Kind kind() {
        return revision.kind();
    }

    /** @return the open subtype token */
    public String subtype() {
        return revision.subtype();
    }
}
