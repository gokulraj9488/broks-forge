"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
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
import { Textarea } from "@/components/ui/textarea";
import { useCreatePrompt } from "@/lib/hooks/use-prompts";
import { getApiErrorMessage } from "@/lib/api/client";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  slug: z
    .string()
    .regex(/^[a-z0-9-]*$/, "Lowercase letters, numbers and hyphens only")
    .optional(),
  description: z.string().max(500).optional(),
  tags: z.string().optional(),
});

type Values = z.infer<typeof schema>;

export function CreatePromptDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreatePrompt(organizationId, projectId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = (values: Values) => {
    const tags = values.tags
      ? values.tags.split(",").map((t) => t.trim()).filter(Boolean)
      : undefined;
    create.mutate(
      {
        name: values.name,
        slug: values.slug || undefined,
        description: values.description || undefined,
        tags,
      },
      {
        onSuccess: (prompt) => {
          toast.success("Prompt created");
          setOpen(false);
          reset();
          router.push(
            `/organizations/${organizationId}/projects/${projectId}/prompts/${prompt.id}`,
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
          New prompt
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create a prompt</DialogTitle>
          <DialogDescription>
            Version-control a prompt template. Add the first version after creating it.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="pr-name" error={errors.name?.message} required>
              <Input id="pr-name" placeholder="Support reply prompt" {...register("name")} />
            </Field>
            <Field label="Slug" htmlFor="pr-slug" error={errors.slug?.message} hint="Optional">
              <Input id="pr-slug" placeholder="support-reply-prompt" {...register("slug")} />
            </Field>
          </div>
          <Field label="Description" htmlFor="pr-desc" error={errors.description?.message}>
            <Textarea id="pr-desc" placeholder="What is this prompt for?" {...register("description")} />
          </Field>
          <Field label="Tags" htmlFor="pr-tags" error={errors.tags?.message} hint="Comma-separated">
            <Input id="pr-tags" placeholder="support, system, v2" {...register("tags")} />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create prompt
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
