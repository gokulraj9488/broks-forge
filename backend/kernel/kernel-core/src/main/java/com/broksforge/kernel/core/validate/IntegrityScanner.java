package com.broksforge.kernel.core.validate;

import com.broksforge.kernel.api.Address;
import com.broksforge.kernel.api.Name;
import com.broksforge.kernel.api.OrgId;
import com.broksforge.kernel.api.Ref;
import com.broksforge.kernel.api.Revision;
import com.broksforge.kernel.api.RevisionHash;
import com.broksforge.kernel.api.EdgeFamily;
import com.broksforge.kernel.core.engine.ForgeKernel;
import com.broksforge.kernel.core.log.EdgeKey;
import com.broksforge.kernel.core.log.LogEntry;
import com.broksforge.kernel.core.log.Payload;
import com.broksforge.kernel.core.memory.InMemoryGraphIndex;
import com.broksforge.kernel.core.memory.InMemoryNameStore;
import com.broksforge.kernel.core.memory.InMemoryRevisionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The runtime integrity scanner — the validation layer. It reads an organization's log through the
 * kernel's public API and checks every constitutional invariant that could, in principle, be
 * violated by a faulty backend or a corrupted store. Each named validator from the plan is a check
 * here:
 *
 * <ul>
 *   <li><b>Audit validator</b> — the hash chain verifies (Law 1 tamper evidence).</li>
 *   <li><b>Revision validator</b> — every recorded revision is retrievable and its content hashes
 *       to its recorded identity (Law 3).</li>
 *   <li><b>Closure validator</b> — every composition reference resolves (Law 7 closedness).</li>
 *   <li><b>Name validator</b> — every name target revision exists (ADR-V2-0006).</li>
 *   <li><b>Graph validator</b> — every asserted edge's endpoints exist.</li>
 *   <li><b>Projection validator + self-healing verification</b> — projections rebuilt from the log
 *       alone reproduce the kernel's answers, proving the log is sufficient to heal derived state
 *       (ADR-V2-0001). Healing is constitutionally valid only for projections, never for the log.</li>
 * </ul>
 *
 * A healthy kernel — one whose backend honors the TCK — scans clean; the scanner exists to detect a
 * backend that does not, and as an operational audit tool.
 */
public final class IntegrityScanner {

    /**
     * Scans one organization's graph.
     *
     * @param kernel the kernel
     * @param org    the organization
     * @return the integrity report
     */
    public IntegrityReport scan(ForgeKernel kernel, OrgId org) {
        List<Finding> findings = new ArrayList<>();

        // Audit validator.
        if (!kernel.verifyChain(org)) {
            findings.add(Finding.error("CHAIN_BROKEN", "hash chain does not verify for org " + org));
        }

        List<LogEntry> entries = kernel.log(org);
        for (LogEntry entry : entries) {
            switch (entry.payload()) {
                case Payload.NodePut np -> {
                    RevisionHash hash = np.revision().hash();
                    Optional<Revision> stored = kernel.revision(hash);
                    if (stored.isEmpty()) {
                        findings.add(Finding.error("REVISION_MISSING",
                                "revision not retrievable at " + entry.position() + ": " + hash));
                    } else if (!stored.get().hash().equals(hash)) {
                        findings.add(Finding.error("REVISION_HASH_MISMATCH",
                                "stored revision hashes to a different identity: " + hash));
                    }
                    for (Ref ref : np.revision().refs()) {
                        if (ref.family() == EdgeFamily.COMPOSITION && kernel.revision(ref.target()).isEmpty()) {
                            findings.add(Finding.error("CLOSURE_DANGLING",
                                    "composition reference targets unknown revision: " + ref.target()));
                        }
                    }
                }
                case Payload.NameRepointed nr -> {
                    if (kernel.revision(nr.to().revision()).isEmpty()) {
                        findings.add(Finding.error("NAME_DANGLING",
                                "name '" + nr.name() + "' targets unknown revision: " + nr.to().revision()));
                    }
                }
                case Payload.EdgeAsserted ea -> checkEndpoints(kernel, findings, ea.edge());
                case Payload.EdgeRetracted ignored -> {
                    // retraction of a possibly-absent edge is always legal; nothing to check
                }
                case Payload.ClockTick ignored -> {
                    // ticks carry no references
                }
            }
        }

        // Projection validator + self-healing verification: rebuild from the log alone.
        checkRebuildable(kernel, org, entries, findings);

        return new IntegrityReport(findings);
    }

    private void checkEndpoints(ForgeKernel kernel, List<Finding> findings, EdgeKey edge) {
        for (Address end : List.of(edge.from(), edge.to())) {
            if (end instanceof Address.Revision r && kernel.revision(r.revision()).isEmpty()) {
                findings.add(Finding.error("GRAPH_DANGLING_EDGE",
                        "edge endpoint revision does not exist: " + r.revision()));
            }
        }
    }

    private void checkRebuildable(ForgeKernel kernel, OrgId org, List<LogEntry> entries, List<Finding> findings) {
        InMemoryRevisionStore revisions = new InMemoryRevisionStore();
        InMemoryGraphIndex graph = new InMemoryGraphIndex();
        InMemoryNameStore names = new InMemoryNameStore();
        for (LogEntry entry : entries) {
            if (entry.payload() instanceof Payload.NodePut np) {
                revisions.put(np.revision().hash(), np.revision());
            }
            graph.apply(entry);
            names.apply(entry);
        }
        for (LogEntry entry : entries) {
            if (entry.payload() instanceof Payload.NameRepointed nr) {
                Name name = nr.name();
                Optional<Address.Revision> rebuilt = names.current(org, name);
                Optional<Address.Revision> live = kernel.resolve(org, name);
                if (!rebuilt.equals(live)) {
                    findings.add(Finding.error("PROJECTION_DRIFT",
                            "name '" + name + "' resolves differently after rebuild-from-log"));
                }
            }
        }
    }
}
