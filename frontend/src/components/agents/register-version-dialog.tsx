"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { useRegisterVersion } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import { ENVIRONMENT_OPTIONS, PROVIDER_OPTIONS } from "@/lib/api/agents";
import { agentVersionSchema, type AgentVersionValues } from "@/lib/validations";

export function RegisterVersionDialog({
  organizationId,
  projectId,
  agentId,
}: {
  organizationId: string;
  projectId: string;
  agentId: string;
}) {
  const [open, setOpen] = useState(false);
  const register_ = useRegisterVersion(organizationId, projectId, agentId);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<AgentVersionValues>({
    resolver: zodResolver(agentVersionSchema),
    defaultValues: { provider: "ANTHROPIC", environment: "PRODUCTION", rollbackReady: true, activate: true },
  });

  const onSubmit = (values: AgentVersionValues) => {
    register_.mutate(
      {
        versionNumber: values.versionNumber,
        model: values.model,
        provider: values.provider,
        frameworkVersion: values.frameworkVersion || undefined,
        gitCommitSha: values.gitCommitSha || undefined,
        promptVersion: values.promptVersion || undefined,
        environment: values.environment,
        releaseNotes: values.releaseNotes || undefined,
        rollbackReady: values.rollbackReady,
        activate: values.activate,
      },
      {
        onSuccess: () => {
          toast.success("Version registered");
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
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Register a version</DialogTitle>
          <DialogDescription>Record a deployment of this agent.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Version number" htmlFor="v-number" error={errors.versionNumber?.message} required>
              <Input id="v-number" placeholder="1.4.0" {...register("versionNumber")} />
            </Field>
            <Field label="Model" htmlFor="v-model" error={errors.model?.message} required>
              <Input id="v-model" placeholder="claude-opus-4-8" {...register("model")} />
            </Field>
            <Field label="Provider" htmlFor="v-provider" error={errors.provider?.message} required>
              <Select id="v-provider" {...register("provider")}>
                {PROVIDER_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Environment" htmlFor="v-env" error={errors.environment?.message} required>
              <Select id="v-env" {...register("environment")}>
                {ENVIRONMENT_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Framework version" htmlFor="v-fw" error={errors.frameworkVersion?.message}>
              <Input id="v-fw" placeholder="1.0.0" {...register("frameworkVersion")} />
            </Field>
            <Field label="Git commit SHA" htmlFor="v-sha" error={errors.gitCommitSha?.message}>
              <Input id="v-sha" placeholder="a1b2c3d" {...register("gitCommitSha")} />
            </Field>
          </div>

          <Field label="Prompt version" htmlFor="v-prompt" error={errors.promptVersion?.message} hint="Optional reference">
            <Input id="v-prompt" placeholder="prompt-v3" {...register("promptVersion")} />
          </Field>

          <Field label="Release notes" htmlFor="v-notes" error={errors.releaseNotes?.message}>
            <Textarea id="v-notes" placeholder="What changed in this deployment?" {...register("releaseNotes")} />
          </Field>

          <div className="flex flex-wrap gap-4">
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="h-4 w-4 accent-[hsl(var(--primary))]" {...register("rollbackReady")} />
              Rollback ready
            </label>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="h-4 w-4 accent-[hsl(var(--primary))]" {...register("activate")} />
              Activate immediately
            </label>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={register_.isPending}>
              Register version
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
