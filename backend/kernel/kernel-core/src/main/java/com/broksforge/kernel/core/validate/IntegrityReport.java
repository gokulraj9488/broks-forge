package com.broksforge.kernel.core.validate;

import java.util.List;

/**
 * The result of an integrity scan: the findings, most-severe first. A report is {@link #clean()} when
 * it contains no ERROR findings.
 *
 * @param findings the findings (may be empty)
 */
public record IntegrityReport(List<Finding> findings) {

    public IntegrityReport {
        findings = List.copyOf(findings);
    }

    /** @return true if there are no ERROR-severity findings */
    public boolean clean() {
        return findings.stream().noneMatch(f -> f.severity() == Finding.Severity.ERROR);
    }

    /** @return the number of ERROR findings */
    public long errorCount() {
        return findings.stream().filter(f -> f.severity() == Finding.Severity.ERROR).count();
    }
}
