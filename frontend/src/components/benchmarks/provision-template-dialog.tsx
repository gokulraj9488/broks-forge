"use client";

import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
import { EmptyState } from "@/components/ui/empty-state";
import { useAgents } from "@/lib/hooks/use-agents";
import { useProviders } from "@/lib/hooks/use-providers";
import { useProvisionGalleryTemplate } from "@/lib/hooks/use-benchmark-gallery";
import { getApiErrorMessage } from "@/lib/api/client";
import type { GalleryTemplateResponse } from "@/lib/api/benchmark-gallery";

const schema = z.object({
  name: z.string().max(160).optional(),
  agentId: z.string().min(1, "Select an agent"),
  judgeProviderId: z.string().optional(),
  judgeModel: z.string().max(128).optional(),
  embeddingProviderId: z.string().optional(),
  embeddingModel: z.string().max(128).optional(),
});

type Values = z.infer<typeof schema>;

export function ProvisionTemplateDialog({
  organizationId,
  projectId,
  template,
  open,
  onOpenChange,
}: {
  organizationId: string;
  projectId: string;
  template: GalleryTemplateResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const router = useRouter();
  const provision = useProvisionGalleryTemplate(organizationId, projectId);

  const { data: agentsData } = useAgents(organizationId, projectId, { size: 100, status: "ACTIVE" });
  const { data: providersData } = useProviders(organizationId, projectId, { size: 100 });
  const agents = agentsData?.content ?? [];
  const providers = providersData?.content ?? [];

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { agentId: "", judgeProviderId: "", judgeModel: "", embeddingProviderId: "", embeddingModel: "" },
  });

  const onSubmit = (values: Values) => {
    if (template.requiresJudgeProvider && !values.judgeProviderId) {
      toast.error("This template requires a judge provider");
      return;
    }
    if (template.requiresEmbeddingProvider && !values.embeddingProviderId && !values.judgeProviderId) {
      toast.error("This template requires an embedding provider");
      return;
    }
    provision.mutate(
      {
        templateKey: template.key,
        agentId: values.agentId,
        judgeProviderId: values.judgeProviderId || undefined,
        judgeModel: values.judgeModel || undefined,
        embeddingProviderId: values.embeddingProviderId || undefined,
        embeddingModel: values.embeddingModel || undefined,
        name: values.name || undefined,
      },
      {
        onSuccess: (result) => {
          toast.success(`"${template.name}" provisioned and running`);
          onOpenChange(false);
          router.push(`/organizations/${organizationId}/projects/${projectId}/evaluations/${result.job.id}`);
        },
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const ready = agents.length > 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Use "{template.name}" template</DialogTitle>
          <DialogDescription>
            Creates a dataset, prompt, and scoring profile from this template, then runs it against your agent.
          </DialogDescription>
        </DialogHeader>

        {!ready ? (
          <EmptyState
            title="No agents yet"
            description="Register an agent before running a gallery template."
          />
        ) : (
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="Agent to evaluate" htmlFor="gt-agent" error={errors.agentId?.message} required>
              <Controller
                control={control}
                name="agentId"
                render={({ field }) => (
                  <Select value={field.value || undefined} onValueChange={field.onChange}>
                    <SelectTrigger id="gt-agent" onBlur={field.onBlur}>
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

            {template.requiresJudgeProvider && (
              <div className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Judge provider"
                  htmlFor="gt-judge-provider"
                  error={errors.judgeProviderId?.message}
                  required
                  hint="Used to score LLM-judged metrics"
                >
                  <Controller
                    control={control}
                    name="judgeProviderId"
                    render={({ field }) => (
                      <Select value={field.value || undefined} onValueChange={field.onChange}>
                        <SelectTrigger id="gt-judge-provider" onBlur={field.onBlur}>
                          <SelectValue placeholder="Select a provider" />
                        </SelectTrigger>
                        <SelectContent>
                          {providers.map((p) => (
                            <SelectItem key={p.id} value={p.id}>
                              {p.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </Field>
                <Field label="Judge model" htmlFor="gt-judge-model" hint="Optional — provider default otherwise">
                  <Input id="gt-judge-model" placeholder="e.g. gemini-2.5-flash" {...register("judgeModel")} />
                </Field>
              </div>
            )}

            {template.requiresEmbeddingProvider && (
              <div className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Embedding provider"
                  htmlFor="gt-embed-provider"
                  hint="Defaults to the judge provider if left blank"
                >
                  <Controller
                    control={control}
                    name="embeddingProviderId"
                    render={({ field }) => (
                      <Select value={field.value || undefined} onValueChange={field.onChange}>
                        <SelectTrigger id="gt-embed-provider" onBlur={field.onBlur}>
                          <SelectValue placeholder="Same as judge provider" />
                        </SelectTrigger>
                        <SelectContent>
                          {providers.map((p) => (
                            <SelectItem key={p.id} value={p.id}>
                              {p.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    )}
                  />
                </Field>
                <Field label="Embedding model" htmlFor="gt-embed-model" hint="Optional — provider default otherwise">
                  <Input id="gt-embed-model" placeholder="e.g. text-embedding-004" {...register("embeddingModel")} />
                </Field>
              </div>
            )}

            <Field label="Name" htmlFor="gt-name" hint={`Optional — defaults to "${template.name}"`}>
              <Input id="gt-name" placeholder={template.name} {...register("name")} />
            </Field>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
                Cancel
              </Button>
              <Button type="submit" loading={provision.isPending}>
                Provision and run
              </Button>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  );
}
