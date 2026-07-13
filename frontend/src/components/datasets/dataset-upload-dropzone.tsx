"use client";

import { useCallback, useRef, useState } from "react";
import axios from "axios";
import { FileSpreadsheet, RotateCcw, UploadCloud, X } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { ColumnMappingDialog, type ColumnMapping } from "@/components/datasets/column-mapping-dialog";
import { usePreviewDatasetUpload, useUploadDatasetFile } from "@/lib/hooks/use-datasets";
import { getApiErrorMessage } from "@/lib/api/client";
import type { DatasetUploadPreviewResponse, DatasetUploadResponse } from "@/lib/api/datasets";

const ACCEPTED_EXTENSIONS = [".csv", ".json", ".xlsx", ".zip"];

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}

function isAcceptedFile(file: File): boolean {
  const lower = file.name.toLowerCase();
  return ACCEPTED_EXTENSIONS.some((ext) => lower.endsWith(ext));
}

const STATUS_BADGE: Record<DatasetUploadResponse["status"], { label: string; variant: "success" | "destructive" | "outline" | "muted" }> = {
  PENDING: { label: "Pending", variant: "muted" },
  PARSING: { label: "Parsing", variant: "outline" },
  COMPLETED: { label: "Completed", variant: "success" },
  DUPLICATE: { label: "Duplicate", variant: "outline" },
  FAILED: { label: "Failed", variant: "destructive" },
};

/**
 * Drag-and-drop / file-picker uploader for CSV, JSON, XLSX and ZIP dataset files, shown alongside
 * (never replacing) the pre-existing paste-mode form. Shows live upload progress, the resulting
 * parser status/row-column counts or error, a Cancel action while in flight, and Retry on failure
 * (simply re-submits the same File — parsing is synchronous per request, so there is nothing
 * server-side to resume).
 */
export function DatasetUploadDropzone({
  organizationId,
  projectId,
  datasetId,
  onUploaded,
}: {
  organizationId: string;
  projectId: string;
  datasetId: string;
  onUploaded?: (upload: DatasetUploadResponse) => void;
}) {
  const upload = useUploadDatasetFile(organizationId, projectId, datasetId);
  const preview = usePreviewDatasetUpload(organizationId, projectId, datasetId);
  const [isDragging, setIsDragging] = useState(false);
  const [lastFile, setLastFile] = useState<File | null>(null);
  const [pendingMapping, setPendingMapping] = useState<{ file: File; preview: DatasetUploadPreviewResponse } | null>(
    null,
  );
  const inputRef = useRef<HTMLInputElement>(null);

  const runUpload = useCallback(
    (file: File, mapping?: ColumnMapping) => {
      setLastFile(file);
      upload.mutate(
        {
          file,
          inputField: mapping?.inputField,
          expectedOutputField: mapping?.expectedOutputField,
          metadataFields: mapping?.metadataFields,
        },
        {
          onSuccess: (result) => {
            if (result.status === "COMPLETED") {
              toast.success(`New version created — ${result.rowCount} rows, ${result.columnCount} columns`);
            } else if (result.status === "DUPLICATE") {
              toast.info("Identical content already exists — no new version created");
            } else if (result.status === "FAILED") {
              toast.error(result.errorMessage ?? "The file could not be parsed");
            }
            onUploaded?.(result);
          },
          onError: (error) => {
            if (axios.isCancel(error)) return; // user-initiated cancel — not a failure to report
            toast.error(getApiErrorMessage(error));
          },
        },
      );
    },
    [upload, onUploaded],
  );

  // Detects the column mapping first: an unambiguous file (the common case — e.g. input/expected_output,
  // or a single recognisable synonym like Bitext's instruction/response) uploads immediately with no
  // extra step; an ambiguous one (zero or multiple candidate columns) opens the mapping dialog instead
  // of silently guessing, which is exactly the bug this flow exists to prevent.
  const startUpload = useCallback(
    (file: File) => {
      if (!isAcceptedFile(file)) {
        toast.error(`Unsupported file type. Accepted: ${ACCEPTED_EXTENSIONS.join(", ")}`);
        return;
      }
      setLastFile(file);
      preview.mutate(file, {
        onSuccess: (result) => {
          if (result.ambiguous) {
            setPendingMapping({ file, preview: result });
          } else {
            runUpload(file, {
              inputField: result.suggestedInputField as string,
              expectedOutputField: result.suggestedExpectedOutputField ?? undefined,
              metadataFields: [],
            });
          }
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      });
    },
    [preview, runUpload],
  );

  const onDrop = useCallback(
    (event: React.DragEvent<HTMLDivElement>) => {
      event.preventDefault();
      setIsDragging(false);
      const file = event.dataTransfer.files?.[0];
      if (file) startUpload(file);
    },
    [startUpload],
  );

  const result = upload.data;

  return (
    <div className="space-y-3">
      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) => e.key === "Enter" && inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={onDrop}
        className={`flex flex-col items-center gap-2 rounded-md border-2 border-dashed p-8 text-center transition-colors cursor-pointer ${
          isDragging ? "border-primary bg-primary/5" : "border-border hover:border-primary/50"
        }`}
      >
        <UploadCloud className="h-8 w-8 text-muted-foreground" />
        <p className="text-sm font-medium">Drag & drop a file, or click to browse</p>
        <p className="text-xs text-muted-foreground">CSV, JSON, XLSX or ZIP — up to 25&nbsp;MB</p>
        <input
          ref={inputRef}
          type="file"
          accept={ACCEPTED_EXTENSIONS.join(",")}
          className="hidden"
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) startUpload(file);
            e.target.value = "";
          }}
        />
      </div>

      {(preview.isPending || upload.isPending || result) && (
        <div className="rounded-md border p-3">
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2">
              <FileSpreadsheet className="h-4 w-4 shrink-0 text-muted-foreground" />
              <span className="truncate text-sm font-medium">{lastFile?.name}</span>
              {lastFile && (
                <span className="shrink-0 text-xs text-muted-foreground">
                  {formatBytes(lastFile.size)}
                </span>
              )}
            </div>
            {upload.isPending ? (
              <Button size="sm" variant="outline" onClick={upload.cancel}>
                <X className="h-3.5 w-3.5" />
                Cancel
              </Button>
            ) : result?.status === "FAILED" && lastFile ? (
              <Button size="sm" variant="outline" onClick={() => startUpload(lastFile)}>
                <RotateCcw className="h-3.5 w-3.5" />
                Retry
              </Button>
            ) : (
              result && <Badge variant={STATUS_BADGE[result.status].variant}>{STATUS_BADGE[result.status].label}</Badge>
            )}
          </div>

          {preview.isPending ? (
            <p className="mt-2 text-xs text-muted-foreground">Detecting columns…</p>
          ) : upload.isPending ? (
            <div className="mt-2 space-y-1">
              <Progress value={upload.progress} />
              <p className="text-xs text-muted-foreground">
                {upload.progress < 100 ? `Uploading… ${upload.progress}%` : "Parsing…"}
              </p>
            </div>
          ) : result ? (
            <p className="mt-2 text-xs text-muted-foreground">
              {result.status === "FAILED"
                ? result.errorMessage
                : `${result.rowCount ?? 0} rows · ${result.columnCount ?? 0} columns · ${result.format}`}
            </p>
          ) : null}
        </div>
      )}

      {pendingMapping && (
        <ColumnMappingDialog
          open
          preview={pendingMapping.preview}
          onCancel={() => setPendingMapping(null)}
          onConfirm={(mapping) => {
            const file = pendingMapping.file;
            setPendingMapping(null);
            runUpload(file, mapping);
          }}
        />
      )}
    </div>
  );
}
