package com.broksforge.modules.report.domain;

/**
 * What a report renders. Each maps to a source aggregate the report is generated
 * from on demand.
 */
public enum ReportType {
    EVALUATION_JOB,
    BENCHMARK,
    REGRESSION
}
