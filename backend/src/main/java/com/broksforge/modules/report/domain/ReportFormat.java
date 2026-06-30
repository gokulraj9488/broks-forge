package com.broksforge.modules.report.domain;

/**
 * Export format. PDF is a deliberate future addition behind the same renderer
 * abstraction (see ADR 0009).
 */
public enum ReportFormat {
    JSON,
    CSV,
    HTML
}
