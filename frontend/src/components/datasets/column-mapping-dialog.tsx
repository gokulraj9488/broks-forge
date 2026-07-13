"use client";

import { useMemo, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field } from "@/components/ui/field";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { DatasetUploadPreviewResponse } from "@/lib/api/datasets";

const NONE = "__none__";

export interface ColumnMapping {
  inputField: string;
  expectedOutputField?: string;
  metadataFields: string[];
}

/**
 * Shown when the server's upload preview can't confidently auto-detect the input/expected-output
 * columns — either several columns could plausibly be one of them, or none matched at all. Presents
 * the auto-detected best guess (editable), every other column as an optional metadata checkbox, and
 * a peek at the first parsed rows so the user can confirm the mapping before anything is imported.
 */
export function ColumnMappingDialog({
  open,
  preview,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  preview: DatasetUploadPreviewResponse;
  onConfirm: (mapping: ColumnMapping) => void;
  onCancel: () => void;
}) {
  const [inputField, setInputField] = useState(preview.suggestedInputField ?? preview.columns[0]);
  const [expectedOutputField, setExpectedOutputField] = useState(preview.suggestedExpectedOutputField ?? NONE);
  const [metadataFields, setMetadataFields] = useState<Set<string>>(
    () => new Set(preview.columns.filter((c) => c !== inputField && c !== expectedOutputField)),
  );

  const availableMetadataColumns = useMemo(
    () => preview.columns.filter((c) => c !== inputField && c !== expectedOutputField),
    [preview.columns, inputField, expectedOutputField],
  );

  const toggleMetadata = (column: string, checked: boolean) => {
    setMetadataFields((prev) => {
      const next = new Set(prev);
      if (checked) next.add(column);
      else next.delete(column);
      return next;
    });
  };

  const previewColumns = preview.columns.slice(0, 6); // keep the preview table compact

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Confirm the column mapping</DialogTitle>
          <DialogDescription>
            {preview.inputCandidates.length === 0
              ? "We couldn't confidently detect which column holds the input — please choose it below."
              : "Several columns could be the input or expected output — please confirm which is which."}
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Input column" htmlFor="mapping-input" required hint="The question/prompt sent to the agent">
            <Select value={inputField} onValueChange={setInputField}>
              <SelectTrigger id="mapping-input">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {preview.columns.map((column) => (
                  <SelectItem key={column} value={column}>
                    {column}
                    {preview.inputCandidates.includes(column) ? " (suggested)" : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>

          <Field
            label="Expected output column"
            htmlFor="mapping-expected"
            hint="Optional — the reference answer to score against"
          >
            <Select value={expectedOutputField} onValueChange={setExpectedOutputField}>
              <SelectTrigger id="mapping-expected">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>None</SelectItem>
                {preview.columns.map((column) => (
                  <SelectItem key={column} value={column}>
                    {column}
                    {preview.expectedOutputCandidates.includes(column) ? " (suggested)" : ""}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </div>

        {availableMetadataColumns.length > 0 && (
          <div>
            <p className="mb-2 text-sm font-medium">Keep as metadata</p>
            <div className="flex flex-wrap gap-x-4 gap-y-2 rounded-md border p-3">
              {availableMetadataColumns.map((column) => (
                <label key={column} className="flex items-center gap-2 text-sm">
                  <Checkbox
                    checked={metadataFields.has(column)}
                    onChange={(e) => toggleMetadata(column, e.target.checked)}
                  />
                  {column}
                </label>
              ))}
            </div>
          </div>
        )}

        <div>
          <p className="mb-2 text-sm font-medium">
            Preview — first {preview.previewRows.length} of {preview.totalRows} rows
          </p>
          <div className="overflow-x-auto rounded-md border">
            <table className="w-full text-left text-xs">
              <thead className="bg-muted">
                <tr>
                  {previewColumns.map((column) => (
                    <th key={column} className="whitespace-nowrap px-3 py-2 font-medium">
                      {column}
                      {column === inputField && " · input"}
                      {column === expectedOutputField && " · expected"}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {preview.previewRows.map((row, i) => (
                  <tr key={i}>
                    {previewColumns.map((column) => (
                      <td key={column} className="max-w-[220px] truncate px-3 py-2 text-muted-foreground">
                        {row[column] ?? ""}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button
            type="button"
            onClick={() =>
              onConfirm({
                inputField,
                expectedOutputField: expectedOutputField === NONE ? undefined : expectedOutputField,
                metadataFields: Array.from(metadataFields),
              })
            }
          >
            Import with this mapping
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
