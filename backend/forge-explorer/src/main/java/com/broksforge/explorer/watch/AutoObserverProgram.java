package com.broksforge.explorer.watch;

import com.broksforge.kernel.api.ActorId;
import com.broksforge.kernel.api.Kind;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.canonical.CanonicalValue;
import com.broksforge.kernel.core.command.AppendCommand;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.event.SubscriptionProgram;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;

/**
 * A reactive {@link SubscriptionProgram} that appends a fact of its own — demonstrating Law 9
 * (no privileged writer): autonomy is just a subscription whose outputs are ordinary appends.
 *
 * <p>When a new <em>artifact</em> is created, this program records an {@code observation} noting the
 * registration, signed by its own {@link ActorId} ({@code program:auto-observer}) exactly as a human's
 * append would be. The reaction cannot cascade: it fires only on artifact node-puts and writes only
 * observations, which never match its own trigger. This is the whole autonomy mechanism the kernel
 * offers — nightly passes, failure→test, suggestions — all built the same way in userspace.
 */
public final class AutoObserverProgram implements SubscriptionProgram {

    /** The actor this program signs its appends with. */
    public static final ActorId ACTOR = ActorId.of("program:auto-observer");

    private int appended;

    /** @return a predicate selecting artifact node-puts (pass to {@code subscribe} as the pattern) */
    public static java.util.function.Predicate<LogEntry> onNewArtifact() {
        return entry -> entry.payload() instanceof Payload.NodePut np
                && np.revision().kind() == Kind.ARTIFACT;
    }

    @Override
    public void onEntry(ForgeKernel kernel, LogEntry entry) {
        if (!(entry.payload() instanceof Payload.NodePut np)) {
            return;
        }
        Revision note = Revision.leaf(Kind.OBSERVATION, "artifact-registered",
                CanonicalValue.objectBuilder()
                        .put("artifact", np.revision().subtype())
                        .put("at_position", entry.position().value())
                        .build());
        kernel.append(entry.org(), new AppendCommand.CreateNode(note), ACTOR);
        appended++;
    }

    /** @return how many observations this program has autonomously appended */
    public int appendedCount() {
        return appended;
    }
}
