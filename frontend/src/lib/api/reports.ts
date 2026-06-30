import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type ReportType = "EVALUATION_JOB" | "BENCHMARK" | "REGRESSION" | "ANALYTICS";
export type ReportFormat = "CSV" | "JSON" | "MARKDOWN";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface ReportResponse {
  id: string;
  name: string;
  type: ReportType;
  format: ReportFormat;
  targetId: string;
  createdAt: string;
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface ExportReportPayload {
  type: ReportType;
  format: ReportFormat;
  targetId: string;
  name?: string;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/reports`;
}

export const reportsApi = {
  list: (organizationId: string, projectId: string, params: PageParams = {}) =>
    apiClient
      .get<PageResponse<ReportResponse>>(base(organizationId, projectId), { params })
      .then((r) => r.data),

  /** Exports a report; the body is a downloadable text payload (CSV/JSON/Markdown). */
  export: (organizationId: string, projectId: string, payload: ExportReportPayload) =>
    apiClient
      .post<string>(`${base(organizationId, projectId)}/export`, payload, {
        responseType: "text",
      })
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const REPORT_TYPE_OPTIONS: { value: ReportType; label: string }[] = [
  { value: "EVALUATION_JOB", label: "Evaluation job" },
  { value: "BENCHMARK", label: "Benchmark" },
  { value: "REGRESSION", label: "Regression check" },
  { value: "ANALYTICS", label: "Analytics" },
];

export const REPORT_FORMAT_OPTIONS: { value: ReportFormat; label: string }[] = [
  { value: "CSV", label: "CSV" },
  { value: "JSON", label: "JSON" },
  { value: "MARKDOWN", label: "Markdown" },
];

const FORMAT_EXTENSION: Record<ReportFormat, string> = {
  CSV: "csv",
  JSON: "json",
  MARKDOWN: "md",
};

const FORMAT_MIME: Record<ReportFormat, string> = {
  CSV: "text/csv",
  JSON: "application/json",
  MARKDOWN: "text/markdown",
};

/** Trigger a browser download of an exported report body. */
export function downloadReport(body: string, name: string, format: ReportFormat) {
  const blob = new Blob([body], { type: FORMAT_MIME[format] });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  const safeName = name.replace(/[^a-z0-9-_]+/gi, "-").toLowerCase() || "report";
  link.download = `${safeName}.${FORMAT_EXTENSION[format]}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
