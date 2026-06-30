package com.broksforge.modules.report.service;

import java.util.List;

/**
 * The format-neutral model of a report: a JSON-serialisable object for JSON exports,
 * plus a tabular projection (headers + rows) for CSV and HTML.
 */
public record ReportData(String title, Object jsonModel, List<String> headers, List<List<String>> rows) {
}
