"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useDeleteOrganization, useUpdateOrganization } from "@/lib/hooks/use-organizations";
import { getApiErrorMessage } from "@/lib/api/client";
import type { OrganizationResponse } from "@/lib/api/types";

const settingsSchema = z.object({
  name: z.string().min(2, "At least 2 characters").max(120),
  description: z.string().max(1000).optional(),
  status: z.enum(["ACTIVE", "ARCHIVED"]),
});
type SettingsValues = z.infer<typeof settingsSchema>;

export function OrganizationSettingsPanel({
  organization,
  canEdit,
  isOwner,
}: {
  organization: OrganizationResponse;
  canEdit: boolean;
  isOwner: boolean;
}) {
  const router = useRouter();
  const update = useUpdateOrganization(organization.id);
  const remove = useDeleteOrganization();
  const [confirmDelete, setConfirmDelete] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<SettingsValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      name: organization.name,
      description: organization.description ?? "",
      status: organization.status,
    },
  });

  const onSubmit = (values: SettingsValues) => {
    update.mutate(
      { name: values.name, description: values.description ?? "", status: values.status },
      {
        onSuccess: () => toast.success("Organization updated"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const handleDelete = () => {
    remove.mutate(organization.id, {
      onSuccess: () => {
        toast.success("Organization deleted");
        router.replace("/organizations");
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setConfirmDelete(false);
      },
    });
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">General</CardTitle>
          <CardDescription>Update your organization&apos;s details.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="Name" htmlFor="settings-name" error={errors.name?.message} required>
              <Input id="settings-name" disabled={!canEdit} {...register("name")} />
            </Field>
            <Field label="Slug" hint="The slug is permanent and cannot be changed.">
              <Input value={organization.slug} disabled readOnly />
            </Field>
            <Field label="Description" htmlFor="settings-description" error={errors.description?.message}>
              <Textarea id="settings-description" disabled={!canEdit} {...register("description")} />
            </Field>
            <Field label="Status" htmlFor="settings-status" error={errors.status?.message}>
              <Select id="settings-status" disabled={!canEdit} {...register("status")}>
                <option value="ACTIVE">Active</option>
                <option value="ARCHIVED">Archived</option>
              </Select>
            </Field>
            {canEdit && (
              <div className="flex justify-end">
                <Button type="submit" loading={update.isPending} disabled={!isDirty}>
                  Save changes
                </Button>
              </div>
            )}
          </form>
        </CardContent>
      </Card>

      {isOwner && (
        <Card className="border-destructive/40">
          <CardHeader>
            <CardTitle className="text-base text-destructive">Danger zone</CardTitle>
            <CardDescription>
              Deleting an organization removes its projects and API keys. This cannot be undone.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
              Delete organization
            </Button>
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete organization?"
        description={`"${organization.name}" and all of its projects and API keys will be permanently removed.`}
        confirmLabel="Delete organization"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
