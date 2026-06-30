"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useArchiveDataset, useDeleteDataset, useUpdateDataset } from "@/lib/hooks/use-datasets";
import { getApiErrorMessage } from "@/lib/api/client";
import { DATASET_VISIBILITY_OPTIONS, type DatasetResponse } from "@/lib/api/datasets";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  description: z.string().max(500).optional(),
  visibility: z.enum(["PRIVATE", "ORGANIZATION", "PUBLIC"]),
  tags: z.string().optional(),
});

type Values = z.infer<typeof schema>;

export function DatasetSettingsPanel({
  dataset,
  organizationId,
  projectId,
  canManage,
  canDelete,
}: {
  dataset: DatasetResponse;
  organizationId: string;
  projectId: string;
  canManage: boolean;
  canDelete: boolean;
}) {
  const router = useRouter();
  const update = useUpdateDataset(organizationId, projectId, dataset.id);
  const archive = useArchiveDataset(organizationId, projectId);
  const remove = useDeleteDataset(organizationId, projectId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const archived = dataset.status === "ARCHIVED";
  const editable = canManage && !archived;

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: dataset.name,
      description: dataset.description ?? "",
      visibility: dataset.visibility,
      tags: dataset.tags.join(", "),
    },
  });

  const onSubmit = (values: Values) => {
    const tags = values.tags ? values.tags.split(",").map((t) => t.trim()).filter(Boolean) : [];
    update.mutate(
      {
        name: values.name,
        description: values.description || undefined,
        visibility: values.visibility,
        tags,
      },
      {
        onSuccess: () => toast.success("Dataset updated"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const toggleArchive = () =>
    archive.mutate(
      { datasetId: dataset.id, archive: !archived },
      {
        onSuccess: () => toast.success(archived ? "Dataset unarchived" : "Dataset archived"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );

  const handleDelete = () =>
    remove.mutate(dataset.id, {
      onSuccess: () => {
        toast.success("Dataset deleted");
        router.replace("/datasets");
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setConfirmDelete(false);
      },
    });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Configuration</CardTitle>
          <CardDescription>
            {archived
              ? "This dataset is archived and read-only. Unarchive it to make changes."
              : "Update this dataset's metadata."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="Name" htmlFor="set-ds-name" error={errors.name?.message} required>
              <Input id="set-ds-name" disabled={!editable} {...register("name")} />
            </Field>
            <Field label="Slug" hint="The slug is permanent and cannot be changed.">
              <Input value={dataset.slug} disabled readOnly />
            </Field>
            <Field label="Description" htmlFor="set-ds-desc" error={errors.description?.message}>
              <Textarea id="set-ds-desc" disabled={!editable} {...register("description")} />
            </Field>
            <Field label="Visibility" htmlFor="set-ds-vis">
              <Select id="set-ds-vis" disabled={!editable} {...register("visibility")}>
                {DATASET_VISIBILITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Tags" htmlFor="set-ds-tags" error={errors.tags?.message} hint="Comma-separated">
              <Input id="set-ds-tags" disabled={!editable} {...register("tags")} />
            </Field>
            {editable && (
              <div className="flex justify-end">
                <Button type="submit" loading={update.isPending} disabled={!isDirty}>
                  Save changes
                </Button>
              </div>
            )}
          </form>
        </CardContent>
      </Card>

      {canManage && (
        <Card className="border-destructive/40">
          <CardHeader>
            <CardTitle className="text-base text-destructive">Danger zone</CardTitle>
            <CardDescription>Archive to make the dataset read-only, or delete it permanently.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-3">
            <Button variant="outline" onClick={toggleArchive} loading={archive.isPending}>
              {archived ? "Unarchive dataset" : "Archive dataset"}
            </Button>
            {canDelete && (
              <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
                Delete dataset
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete dataset?"
        description={`"${dataset.name}" and all of its versions and items will be permanently removed.`}
        confirmLabel="Delete dataset"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
