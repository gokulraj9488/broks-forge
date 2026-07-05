"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Play } from "lucide-react";
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
import { useCreateEvaluationJob } from "@/lib/hooks/use-evaluation-jobs";
import { useAgents } from "@/lib/hooks/use-agents";
import { useDatasets } from "@/lib/hooks/use-datasets";
import { usePrompts } from "@/lib/hooks/use-prompts";
import { useEvaluationProfiles } from "@/lib/hooks/use-evaluation-profiles";
import { getApiErrorMessage } from "@/lib/api/client";
import { PROVIDER_OPTIONS, type LlmProvider } from "@/lib/api/agents";

const schema = z.object({
  name: z.string().min(1, "Name is required").max(120),
  agentId: z.string().min(1, "Select an agent"),
  datasetId: z.string().min(1, "Select a dataset"),
  promptId: z.string().optional(),
  profileId: z.string().optional(),
  provider: z.string().optional(),
  model: z.string().optional(),
  autoRun: z.boolean().optional(),
});

type Values = z.infer<typeof schema>;

// Radix Select forbids value=""; map the "none" options through this sentinel
// so the form state (and the submitted payload) still uses "".
const NONE = "none";

export function CreateJobDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreateEvaluationJob(organizationId, projectId);

  const { data: agentsData } = useAgents(organizationId, projectId, { size: 100, status: "ACTIVE" });
  const { data: datasetsData } = useDatasets(organizationId, projectId, { size: 100, status: "ACTIVE" });
  const { data: promptsData } = usePrompts(organizationId, projectId, { size: 100, status: "ACTIVE" });
  const { data: profilesData } = useEvaluationProfiles(organizationId, projectId, { size: 100 });

  const agents = agentsData?.content ?? [];
  const datasets = datasetsData?.content ?? [];
  const prompts = promptsData?.content ?? [];
  const profiles = profilesData?.content ?? [];

  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      agentId: "",
      datasetId: "",
      promptId: "",
      profileId: "",
      provider: "",
      autoRun: true,
    },
  });

  const onSubmit = (values: Values) => {
    create.mutate(
      {
        name: values.name,
        agentId: values.agentId,
        datasetId: values.datasetId,
        promptId: values.promptId || undefined,
        profileId: values.profileId || undefined,
        provider: (values.provider as LlmProvider) || undefined,
        model: values.model || undefined,
        autoRun: values.autoRun,
      },
      {
        onSuccess: (job) => {
          toast.success(values.autoRun ? "Evaluation started" : "Evaluation created");
          setOpen(false);
          reset();
          router.push(
            `/organizations/${organizationId}/projects/${projectId}/evaluations/${job.id}`,
          );
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const noInputs = agents.length === 0 || datasets.length === 0;

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Play className="h-4 w-4" />
          New evaluation
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Run an evaluation</DialogTitle>
          <DialogDescription>
            Evaluate an agent against a dataset, optionally with a prompt and scoring profile.
          </DialogDescription>
        </DialogHeader>

        {noInputs ? (
          <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
            You need at least one active agent and one dataset before running an evaluation.
          </p>
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="Name" htmlFor="ej-name" error={errors.name?.message} required>
              <Input id="ej-name" placeholder="Support agent · golden set" {...register("name")} />
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Agent" htmlFor="ej-agent" error={errors.agentId?.message} required>
                <Controller
                  control={control}
                  name="agentId"
                  render={({ field }) => (
                    <Select value={field.value || undefined} onValueChange={field.onChange}>
                      <SelectTrigger id="ej-agent" onBlur={field.onBlur}>
                        <SelectValue placeholder="Select an agent" />
                      </SelectTrigger>
                      <SelectContent>
                        {agents.map((a) => (
                          <SelectItem key={a.id} value={a.id}>
                            {a.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Field>
              <Field label="Dataset" htmlFor="ej-dataset" error={errors.datasetId?.message} required>
                <Controller
                  control={control}
                  name="datasetId"
                  render={({ field }) => (
                    <Select value={field.value || undefined} onValueChange={field.onChange}>
                      <SelectTrigger id="ej-dataset" onBlur={field.onBlur}>
                        <SelectValue placeholder="Select a dataset" />
                      </SelectTrigger>
                      <SelectContent>
                        {datasets.map((d) => (
                          <SelectItem key={d.id} value={d.id}>
                            {d.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Field>
              <Field label="Prompt" htmlFor="ej-prompt" error={errors.promptId?.message} hint="Optional">
                <Controller
                  control={control}
                  name="promptId"
                  render={({ field }) => (
                    <Select
                      value={field.value === "" ? NONE : field.value}
                      onValueChange={(v) => field.onChange(v === NONE ? "" : v)}
                    >
                      <SelectTrigger id="ej-prompt" onBlur={field.onBlur}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>No prompt</SelectItem>
                        {prompts.map((p) => (
                          <SelectItem key={p.id} value={p.id}>
                            {p.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Field>
              <Field label="Profile" htmlFor="ej-profile" error={errors.profileId?.message} hint="Scoring metrics">
                <Controller
                  control={control}
                  name="profileId"
                  render={({ field }) => (
                    <Select
                      value={field.value === "" ? NONE : field.value}
                      onValueChange={(v) => field.onChange(v === NONE ? "" : v)}
                    >
                      <SelectTrigger id="ej-profile" onBlur={field.onBlur}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>No profile</SelectItem>
                        {profiles.map((p) => (
                          <SelectItem key={p.id} value={p.id}>
                            {p.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Field>
              <Field label="Provider" htmlFor="ej-provider" error={errors.provider?.message} hint="Optional override">
                <Controller
                  control={control}
                  name="provider"
                  render={({ field }) => (
                    <Select
                      value={field.value === "" ? NONE : field.value}
                      onValueChange={(v) => field.onChange(v === NONE ? "" : v)}
                    >
                      <SelectTrigger id="ej-provider" onBlur={field.onBlur}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={NONE}>Agent default</SelectItem>
                        {PROVIDER_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Field>
              <Field label="Model" htmlFor="ej-model" error={errors.model?.message} hint="Optional override">
                <Input id="ej-model" placeholder="claude-opus-4-8" {...register("model")} />
              </Field>
            </div>

            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" className="h-4 w-4 accent-[hsl(var(--primary))]" {...register("autoRun")} />
              Run immediately after creating
            </label>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={create.isPending}>
                Create evaluation
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
