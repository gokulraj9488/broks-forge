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
import { Textarea } from "@/components/ui/textarea";
import { useArchivePrompt, useDeletePrompt, useUpdatePrompt } from "@/lib/hooks/use-prompts";
import { getApiErrorMessage } from "@/lib/api/client";
import type { PromptResponse } from "@/lib/api/prompts";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  description: z.string().max(500).optional(),
  tags: z.string().optional(),
});

type Values = z.infer<typeof schema>;

export function PromptSettingsPanel({
  prompt,
  organizationId,
  projectId,
  canManage,
  canDelete,
}: {
  prompt: PromptResponse;
  organizationId: string;
  projectId: string;
  canManage: boolean;
  canDelete: boolean;
}) {
  const router = useRouter();
  const update = useUpdatePrompt(organizationId, projectId, prompt.id);
  const archive = useArchivePrompt(organizationId, projectId);
  const remove = useDeletePrompt(organizationId, projectId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const archived = prompt.status === "ARCHIVED";
  const editable = canManage && !archived;

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: prompt.name,
      description: prompt.description ?? "",
      tags: prompt.tags.join(", "),
    },
  });

  const onSubmit = (values: Values) => {
    const tags = values.tags ? values.tags.split(",").map((t) => t.trim()).filter(Boolean) : [];
    update.mutate(
      { name: values.name, description: values.description || undefined, tags },
      {
        onSuccess: () => toast.success("Prompt updated"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const toggleArchive = () =>
    archive.mutate(
      { promptId: prompt.id, archive: !archived },
      {
        onSuccess: () => toast.success(archived ? "Prompt unarchived" : "Prompt archived"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );

  const handleDelete = () =>
    remove.mutate(prompt.id, {
      onSuccess: () => {
        toast.success("Prompt deleted");
        router.replace("/prompts");
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
              ? "This prompt is archived and read-only. Unarchive it to make changes."
              : "Update this prompt's metadata."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="Name" htmlFor="set-pr-name" error={errors.name?.message} required>
              <Input id="set-pr-name" disabled={!editable} {...register("name")} />
            </Field>
            <Field label="Slug" hint="The slug is permanent and cannot be changed.">
              <Input value={prompt.slug} disabled readOnly />
            </Field>
            <Field label="Description" htmlFor="set-pr-desc" error={errors.description?.message}>
              <Textarea id="set-pr-desc" disabled={!editable} {...register("description")} />
            </Field>
            <Field label="Tags" htmlFor="set-pr-tags" error={errors.tags?.message} hint="Comma-separated">
              <Input id="set-pr-tags" disabled={!editable} {...register("tags")} />
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
            <CardDescription>Archive to make the prompt read-only, or delete it permanently.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-3">
            <Button variant="outline" onClick={toggleArchive} loading={archive.isPending}>
              {archived ? "Unarchive prompt" : "Archive prompt"}
            </Button>
            {canDelete && (
              <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
                Delete prompt
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete prompt?"
        description={`"${prompt.name}" and all of its versions will be permanently removed.`}
        confirmLabel="Delete prompt"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
