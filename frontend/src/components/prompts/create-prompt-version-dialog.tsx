"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { GitBranch } from "lucide-react";
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
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useCreatePromptVersion } from "@/lib/hooks/use-prompts";
import { getApiErrorMessage } from "@/lib/api/client";
import { PROVIDER_OPTIONS, type LlmProvider } from "@/lib/api/agents";

const schema = z.object({
  template: z.string().min(1, "Template is required"),
  notes: z.string().max(500).optional(),
  provider: z.string().optional(),
  model: z.string().optional(),
  activate: z.boolean().optional(),
});

type Values = z.infer<typeof schema>;

export function CreatePromptVersionDialog({
  organizationId,
  projectId,
  promptId,
}: {
  organizationId: string;
  projectId: string;
  promptId: string;
}) {
  const [open, setOpen] = useState(false);
  const create = useCreatePromptVersion(organizationId, projectId, promptId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { activate: true },
  });

  const onSubmit = (values: Values) => {
    create.mutate(
      {
        template: values.template,
        notes: values.notes || undefined,
        provider: (values.provider as LlmProvider) || undefined,
        model: values.model || undefined,
        activate: values.activate,
      },
      {
        onSuccess: (version) => {
          toast.success(`Version ${version.versionNumber} created`);
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
          <GitBranch className="h-4 w-4" />
          New version
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>New prompt version</DialogTitle>
          <DialogDescription>
            Use {"{{variable}}"} placeholders — they are detected automatically.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <Field label="Template" htmlFor="pv-template" error={errors.template?.message} required>
            <Textarea
              id="pv-template"
              className="min-h-[180px] font-mono text-xs"
              placeholder={"You are a helpful assistant.\n\nUser: {{question}}\nAnswer:"}
              {...register("template")}
            />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Provider" htmlFor="pv-provider" error={errors.provider?.message} hint="Optional">
              <Select id="pv-provider" defaultValue="" {...register("provider")}>
                <option value="">Unspecified</option>
                {PROVIDER_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Model" htmlFor="pv-model" error={errors.model?.message} hint="Optional">
              <Input id="pv-model" placeholder="claude-opus-4-8" {...register("model")} />
            </Field>
          </div>
          <Field label="Notes" htmlFor="pv-notes" error={errors.notes?.message} hint="What changed?">
            <Textarea id="pv-notes" {...register("notes")} />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-[hsl(var(--primary))]" {...register("activate")} />
            Activate this version immediately
          </label>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Create version
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
