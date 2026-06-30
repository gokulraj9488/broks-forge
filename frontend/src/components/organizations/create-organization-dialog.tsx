"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { Textarea } from "@/components/ui/textarea";
import { useCreateOrganization } from "@/lib/hooks/use-organizations";
import { getApiErrorMessage } from "@/lib/api/client";
import { organizationSchema, type OrganizationValues } from "@/lib/validations";

export function CreateOrganizationDialog() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreateOrganization();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<OrganizationValues>({ resolver: zodResolver(organizationSchema) });

  const onSubmit = (values: OrganizationValues) => {
    create.mutate(
      { name: values.name, slug: values.slug || undefined, description: values.description || undefined },
      {
        onSuccess: (org) => {
          toast.success("Organization created");
          setOpen(false);
          reset();
          router.push(`/organizations/${org.id}`);
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4" />
          New organization
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create organization</DialogTitle>
          <DialogDescription>Organizations group your team, projects and API keys.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Name" htmlFor="org-name" error={errors.name?.message} required>
            <Input id="org-name" placeholder="Acme AI" {...register("name")} />
          </Field>
          <Field
            label="Slug"
            htmlFor="org-slug"
            error={errors.slug?.message}
            hint="Optional. Generated from the name if left blank."
          >
            <Input id="org-slug" placeholder="acme-ai" {...register("slug")} />
          </Field>
          <Field label="Description" htmlFor="org-description" error={errors.description?.message}>
            <Textarea
              id="org-description"
              placeholder="What is this organization about?"
              {...register("description")}
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
