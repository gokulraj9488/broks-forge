import type { AxiosProgressEvent } from "axios";
import { apiClient } from "@/lib/api/client";
import type { PageParams } from "@/lib/api/organizations";
import type { PageResponse } from "@/lib/api/types";

// ---------------------------------------------------------------------------
// Enums
// ---------------------------------------------------------------------------
export type DatasetVisibility = "PRIVATE" | "ORGANIZATION" | "PUBLIC";
export type DatasetStatus = "ACTIVE" | "ARCHIVED";
export type DatasetFormat = "CSV" | "JSON";
/** Formats a version/upload can report; distinct from {@link DatasetFormat} (the paste-mode picker),
 * since XLSX/ZIP only ever arrive via file upload, never via the pasted-content endpoint. */
export type DatasetSourceFormat = DatasetFormat | "MANUAL" | "XLSX" | "ZIP";
export type DatasetUploadStatus = "PENDING" | "PARSING" | "COMPLETED" | "DUPLICATE" | "FAILED";

// ---------------------------------------------------------------------------
// DTOs
// ---------------------------------------------------------------------------
export interface DatasetSummaryResponse {
  id: string;
  name: string;
  slug: string;
  visibility: DatasetVisibility;
  status: DatasetStatus;
  tags: string[];
  latestVersionNumber: number | null;
  currentItemCount: number | null;
  updatedAt: string;
}

export interface DatasetResponse extends DatasetSummaryResponse {
  organizationId: string;
  projectId: string;
  description: string | null;
  ownerId: string;
  currentVersionId: string | null;
  createdAt: string;
}

export interface DatasetVersionResponse {
  id: string;
  datasetId: string;
  versionNumber: number;
  sourceFormat: DatasetSourceFormat;
  itemCount: number;
  columns: string[];
  checksum: string;
  description?: string | null;
  createdAt: string;
}

export interface DatasetUploadResponse {
  id: string;
  datasetId: string;
  filename: string;
  contentType: string | null;
  format: DatasetSourceFormat;
  sizeBytes: number;
  checksum: string;
  status: DatasetUploadStatus;
  rowCount: number | null;
  columnCount: number | null;
  datasetVersionId: string | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface DatasetUploadPreviewResponse {
  columns: string[];
  suggestedInputField: string | null;
  suggestedExpectedOutputField: string | null;
  inputCandidates: string[];
  expectedOutputCandidates: string[];
  /** True when the mapping could not be confidently auto-detected (zero or multiple candidates) —
   * the caller should show a mapping dialog rather than upload straight away. */
  ambiguous: boolean;
  previewRows: Record<string, string>[];
  totalRows: number;
}

export interface DatasetItemResponse {
  id: string;
  sequence: number;
  input: string;
  expectedOutput: string | null;
  metadata: Record<string, unknown> | null;
}

export interface DatasetStatsResponse {
  versionNumber: number;
  itemCount: number;
  itemsWithExpectedOutput: number;
  expectedOutputCoverage: number;
  avgInputLength: number;
  avgExpectedOutputLength: number;
  columns: string[];
}

// ---------------------------------------------------------------------------
// Request payloads
// ---------------------------------------------------------------------------
export interface CreateDatasetPayload {
  name: string;
  slug?: string;
  description?: string;
  visibility?: DatasetVisibility;
  tags?: string[];
}

export type UpdateDatasetPayload = Partial<{
  name: string;
  description: string;
  visibility: DatasetVisibility;
  tags: string[];
}>;

export interface CreateDatasetVersionPayload {
  format: DatasetFormat;
  content: string;
  description?: string;
  inputField?: string;
  expectedOutputField?: string;
}

export interface DatasetFilterParams extends PageParams {
  q?: string;
  status?: DatasetStatus;
  visibility?: DatasetVisibility;
  tag?: string;
}

// ---------------------------------------------------------------------------
// Endpoints
// ---------------------------------------------------------------------------
function base(organizationId: string, projectId: string) {
  return `/api/v1/organizations/${organizationId}/projects/${projectId}/datasets`;
}

export const datasetsApi = {
  list: (organizationId: string, projectId: string, params: DatasetFilterParams = {}) =>
    apiClient
      .get<PageResponse<DatasetSummaryResponse>>(base(organizationId, projectId), { params })
      .then((r) => r.data),

  get: (organizationId: string, projectId: string, datasetId: string) =>
    apiClient
      .get<DatasetResponse>(`${base(organizationId, projectId)}/${datasetId}`)
      .then((r) => r.data),

  create: (organizationId: string, projectId: string, payload: CreateDatasetPayload) =>
    apiClient
      .post<DatasetResponse>(base(organizationId, projectId), payload)
      .then((r) => r.data),

  update: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    payload: UpdateDatasetPayload,
  ) =>
    apiClient
      .patch<DatasetResponse>(`${base(organizationId, projectId)}/${datasetId}`, payload)
      .then((r) => r.data),

  remove: (organizationId: string, projectId: string, datasetId: string) =>
    apiClient
      .delete<void>(`${base(organizationId, projectId)}/${datasetId}`)
      .then((r) => r.data),

  archive: (organizationId: string, projectId: string, datasetId: string) =>
    apiClient
      .post<DatasetResponse>(`${base(organizationId, projectId)}/${datasetId}/archive`)
      .then((r) => r.data),

  unarchive: (organizationId: string, projectId: string, datasetId: string) =>
    apiClient
      .post<DatasetResponse>(`${base(organizationId, projectId)}/${datasetId}/unarchive`)
      .then((r) => r.data),

  createVersion: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    payload: CreateDatasetVersionPayload,
  ) =>
    apiClient
      .post<DatasetVersionResponse>(
        `${base(organizationId, projectId)}/${datasetId}/versions`,
        payload,
      )
      .then((r) => r.data),

  listVersions: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    params: PageParams = {},
  ) =>
    apiClient
      .get<PageResponse<DatasetVersionResponse>>(
        `${base(organizationId, projectId)}/${datasetId}/versions`,
        { params },
      )
      .then((r) => r.data),

  listItems: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    versionId: string,
    params: PageParams = {},
  ) =>
    apiClient
      .get<PageResponse<DatasetItemResponse>>(
        `${base(organizationId, projectId)}/${datasetId}/versions/${versionId}/items`,
        { params },
      )
      .then((r) => r.data),

  stats: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    versionId?: string,
  ) =>
    apiClient
      .get<DatasetStatsResponse>(`${base(organizationId, projectId)}/${datasetId}/stats`, {
        params: versionId ? { versionId } : undefined,
      })
      .then((r) => r.data),

  /**
   * Uploads a CSV/JSON/XLSX/ZIP file (multipart). Accepts an AbortSignal so the caller can cancel
   * an in-flight upload, and an onUploadProgress callback for a progress bar; parsing itself
   * completes synchronously within this request, so the resolved response already carries the
   * terminal parser status (COMPLETED/DUPLICATE/FAILED).
   */
  uploadFile: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    file: File,
    options: {
      description?: string;
      inputField?: string;
      expectedOutputField?: string;
      /** Restricts which extra columns become item metadata; omit to keep every remaining column. */
      metadataFields?: string[];
      signal?: AbortSignal;
      onUploadProgress?: (event: AxiosProgressEvent) => void;
    } = {},
  ) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient
      .post<DatasetUploadResponse>(
        `${base(organizationId, projectId)}/${datasetId}/uploads`,
        formData,
        {
          params: {
            description: options.description || undefined,
            inputField: options.inputField || undefined,
            expectedOutputField: options.expectedOutputField || undefined,
            metadataFields: options.metadataFields?.length ? options.metadataFields.join(",") : undefined,
          },
          // Clear the client's default "application/json" so the browser sets
          // "multipart/form-data; boundary=..." itself for this FormData body.
          headers: { "Content-Type": undefined },
          signal: options.signal,
          onUploadProgress: options.onUploadProgress,
        },
      )
      .then((r) => r.data);
  },

  /**
   * Dry-run: detects columns and suggests an input/expected-output mapping without creating a
   * version. Call this before {@link uploadFile} so the UI can skip the mapping dialog when the
   * mapping is unambiguous, and pre-fill it (with a data preview) when it is not.
   */
  previewUpload: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    file: File,
    signal?: AbortSignal,
  ) => {
    const formData = new FormData();
    formData.append("file", file);
    return apiClient
      .post<DatasetUploadPreviewResponse>(
        `${base(organizationId, projectId)}/${datasetId}/uploads/preview`,
        formData,
        { headers: { "Content-Type": undefined }, signal },
      )
      .then((r) => r.data);
  },

  listUploads: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    params: PageParams = {},
  ) =>
    apiClient
      .get<PageResponse<DatasetUploadResponse>>(
        `${base(organizationId, projectId)}/${datasetId}/uploads`,
        { params },
      )
      .then((r) => r.data),

  getUpload: (
    organizationId: string,
    projectId: string,
    datasetId: string,
    uploadId: string,
  ) =>
    apiClient
      .get<DatasetUploadResponse>(
        `${base(organizationId, projectId)}/${datasetId}/uploads/${uploadId}`,
      )
      .then((r) => r.data),
};

// ---------------------------------------------------------------------------
// UI option lists
// ---------------------------------------------------------------------------
export const DATASET_VISIBILITY_OPTIONS: { value: DatasetVisibility; label: string }[] = [
  { value: "PRIVATE", label: "Private" },
  { value: "ORGANIZATION", label: "Organization" },
  { value: "PUBLIC", label: "Public" },
];

export const DATASET_FORMAT_OPTIONS: { value: DatasetFormat; label: string }[] = [
  { value: "CSV", label: "CSV" },
  { value: "JSON", label: "JSON" },
];
