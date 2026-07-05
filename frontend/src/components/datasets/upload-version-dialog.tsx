"use client";

import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Upload } from "lucide-react";
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
import { useCreateDatasetVersion } from "@/lib/hooks/use-datasets";
import { getApiErrorMessage } from "@/lib/api/client";
import { DATASET_FORMAT_OPTIONS } from "@/lib/api/datasets";

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
            Paste CSV or JSON content. Map which columns hold the input and the expected output.
          </DialogDescription>
        </DialogHeader>
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
      </DialogContent>
    </Dialog>
  );
}
