"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Plus } from "lucide-react";
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
import { useCreateDataset } from "@/lib/hooks/use-datasets";
import { getApiErrorMessage } from "@/lib/api/client";
import { DATASET_VISIBILITY_OPTIONS } from "@/lib/api/datasets";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  slug: z
    .string()
    .regex(/^[a-z0-9-]*$/, "Lowercase letters, numbers and hyphens only")
    .optional(),
  description: z.string().max(500).optional(),
  visibility: z.enum(["PRIVATE", "ORGANIZATION", "PUBLIC"]),
  tags: z.string().optional(),
});

type Values = z.infer<typeof schema>;

export function CreateDatasetDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreateDataset(organizationId, projectId);
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { visibility: "PRIVATE" },
  });

  const onSubmit = (values: Values) => {
    const tags = values.tags
      ? values.tags.split(",").map((t) => t.trim()).filter(Boolean)
      : undefined;
    create.mutate(
      {
        name: values.name,
        slug: values.slug || undefined,
        description: values.description || undefined,
        visibility: values.visibility,
        tags,
      },
      {
        onSuccess: (dataset) => {
          toast.success("Dataset created");
          setOpen(false);
          reset();
          router.push(
            `/organizations/${organizationId}/projects/${projectId}/datasets/${dataset.id}`,
          );
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="h-4 w-4" />
          New dataset
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create a dataset</DialogTitle>
          <DialogDescription>
            Group evaluation inputs and expected outputs. Upload data as a version next.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="ds-name" error={errors.name?.message} required>
              <Input id="ds-name" placeholder="Support QA set" {...register("name")} />
            </Field>
            <Field label="Slug" htmlFor="ds-slug" error={errors.slug?.message} hint="Optional">
              <Input id="ds-slug" placeholder="support-qa-set" {...register("slug")} />
            </Field>
          </div>
          <Field label="Description" htmlFor="ds-desc" error={errors.description?.message}>
            <Textarea id="ds-desc" placeholder="What is this dataset for?" {...register("description")} />
          </Field>
          <Field label="Visibility" htmlFor="ds-vis" error={errors.visibility?.message} required>
            <Controller
              control={control}
              name="visibility"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger id="ds-vis" onBlur={field.onBlur}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DATASET_VISIBILITY_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
          </Field>
          <Field label="Tags" htmlFor="ds-tags" error={errors.tags?.message} hint="Comma-separated">
            <Input id="ds-tags" placeholder="qa, golden, tier-1" {...register("tags")} />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create dataset
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
