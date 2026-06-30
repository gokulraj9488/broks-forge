"use client";

import { useState } from "react";
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
import { useCreateProject } from "@/lib/hooks/use-projects";
import { getApiErrorMessage } from "@/lib/api/client";
import { projectSchema, type ProjectValues } from "@/lib/validations";

export function CreateProjectDialog({ organizationId }: { organizationId: string }) {
  const [open, setOpen] = useState(false);
  const create = useCreateProject(organizationId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProjectValues>({ resolver: zodResolver(projectSchema) });

  const onSubmit = (values: ProjectValues) => {
    create.mutate(
      { name: values.name, slug: values.slug || undefined, description: values.description || undefined },
      {
        onSuccess: () => {
          toast.success("Project created");
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
          <Plus className="h-4 w-4" />
          New project
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create project</DialogTitle>
          <DialogDescription>Projects hold your agents, keys and (soon) evaluations.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Name" htmlFor="project-name" error={errors.name?.message} required>
            <Input id="project-name" placeholder="Customer Support Agent" {...register("name")} />
          </Field>
          <Field
            label="Slug"
            htmlFor="project-slug"
            error={errors.slug?.message}
            hint="Optional. Generated from the name if left blank."
          >
            <Input id="project-slug" placeholder="customer-support-agent" {...register("slug")} />
          </Field>
          <Field label="Description" htmlFor="project-description" error={errors.description?.message}>
            <Textarea id="project-description" placeholder="What does this project do?" {...register("description")} />
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
