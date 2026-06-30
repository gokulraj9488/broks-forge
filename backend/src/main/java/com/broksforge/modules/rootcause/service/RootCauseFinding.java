package com.broksforge.modules.rootcause.service;

import com.broksforge.modules.advisor.domain.Confidence;
import com.broksforge.modules.advisor.domain.Severity;

import java.util.List;

/**
 * One diagnosed cause behind a failed evaluation, regression, benchmark gap or anomaly
 * (ADR 0012). The shape is fixed to exactly what an engineer needs to act: the root
 * cause, supporting evidence, confidence, the recommended fix, the expected improvement
 * and severity — plus an optional link to the Engineering Knowledge Graph.
 */
public record RootCauseFinding(
        String rootCause,
        Severity severity,
        Confidence confidence,
        List<String> evidence,
        String recommendation,
        String expectedImprovement,
        String knowledgeKey
) {
    public RootCauseFinding {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
