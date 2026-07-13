"use client";

import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { FileText, Upload } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { DatasetUploadDropzone } from "@/components/datasets/dataset-upload-dropzone";
import { useCreateDatasetVersion } from "@/lib/hooks/use-datasets";
import { getApiErrorMessage } from "@/lib/api/client";
import { DATASET_FORMAT_OPTIONS } from "@/lib/api/datasets";
import { cn } from "@/lib/utils";

const schema = z.object({
  format: z.enum(["CSV", "JSON"]),
  content: z.string().min(1, "Provide the dataset content"),
  description: z.string().max(500).optional(),
  inputField: z.string().optional(),
  expectedOutputField: z.string().optional(),
});

type Values = z.infer<typeof schema>;

const PLACEHOLDER: Record<string, string> = {
  CSV: "input,expected_output\nWhat is 2+2?,4\nCapital of France?,Paris",
  JSON: '[\n  { "input": "What is 2+2?", "expectedOutput": "4" },\n  { "input": "Capital of France?", "expectedOutput": "Paris" }\n]',
};

export function UploadVersionDialog({
  organizationId,
  projectId,
  datasetId,
}: {
  organizationId: string;
  projectId: string;
  datasetId: string;
}) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<"upload" | "paste">("upload");
  const create = useCreateDatasetVersion(organizationId, projectId, datasetId);
  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { format: "CSV" },
  });

  const format = watch("format");

  const onSubmit = (values: Values) => {
    create.mutate(
      {
        format: values.format,
        content: values.content,
        description: values.description || undefined,
        inputField: values.inputField || undefined,
        expectedOutputField: values.expectedOutputField || undefined,
      },
      {
        onSuccess: (version) => {
          toast.success(`Version ${version.versionNumber} created · ${version.itemCount} items`);
          setOpen(false);
          reset();
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Upload className="h-4 w-4" />
          Upload version
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Upload a new version</DialogTitle>
          <DialogDescription>
            {mode === "upload"
              ? "Drag & drop a CSV, JSON, XLSX or ZIP file, or paste content instead."
              : "Paste CSV or JSON content. Map which columns hold the input and the expected output."}
          </DialogDescription>
        </DialogHeader>

        <div className="flex gap-1 rounded-md bg-muted p-1 text-sm">
          <button
            type="button"
            onClick={() => setMode("upload")}
            className={cn(
              "flex-1 rounded-sm px-3 py-1.5 font-medium transition-colors",
              mode === "upload" ? "bg-background shadow-sm" : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Upload className="mr-1.5 inline h-3.5 w-3.5" />
            Upload file
          </button>
          <button
            type="button"
            onClick={() => setMode("paste")}
            className={cn(
              "flex-1 rounded-sm px-3 py-1.5 font-medium transition-colors",
              mode === "paste" ? "bg-background shadow-sm" : "text-muted-foreground hover:text-foreground",
            )}
          >
            <FileText className="mr-1.5 inline h-3.5 w-3.5" />
            Paste content
          </button>
        </div>

        {mode === "upload" ? (
          <DatasetUploadDropzone
            organizationId={organizationId}
            projectId={projectId}
            datasetId={datasetId}
            onUploaded={(result) => {
              if (result.status === "COMPLETED" || result.status === "DUPLICATE") {
                setOpen(false);
              }
            }}
          />
        ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-3">
            <Field label="Format" htmlFor="dv-format" error={errors.format?.message} required>
              <Controller
                control={control}
                name="format"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="dv-format" onBlur={field.onBlur}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {DATASET_FORMAT_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            <Field label="Input field" htmlFor="dv-input" error={errors.inputField?.message} hint="Optional">
              <Input id="dv-input" placeholder="input" {...register("inputField")} />
            </Field>
            <Field
              label="Expected output field"
              htmlFor="dv-expected"
              error={errors.expectedOutputField?.message}
              hint="Optional"
            >
              <Input id="dv-expected" placeholder="expected_output" {...register("expectedOutputField")} />
            </Field>
          </div>

          <Field label="Content" htmlFor="dv-content" error={errors.content?.message} required>
            <Textarea
              id="dv-content"
              className="min-h-[200px] font-mono text-xs"
              placeholder={PLACEHOLDER[format] ?? ""}
              {...register("content")}
            />
          </Field>

          <Field label="Description" htmlFor="dv-desc" error={errors.description?.message} hint="What changed in this version?">
            <Textarea id="dv-desc" {...register("description")} />
          </Field>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Upload version
            </Button>
          </DialogFooter>
        </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
