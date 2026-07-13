"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { InheritedProviderPanel } from "@/components/providers/inherited-provider-panel";
import { useCreateAgent } from "@/lib/hooks/use-agents";
import { useProviders } from "@/lib/hooks/use-providers";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  AUTH_TYPE_OPTIONS,
  FRAMEWORK_OPTIONS,
  LANGUAGE_OPTIONS,
  VISIBILITY_OPTIONS,
} from "@/lib/api/agents";
import { agentSchema, type AgentValues } from "@/lib/validations";

// Radix Select forbids value=""; map the "no provider" option through this sentinel so the
// form state (and the submitted payload) still uses undefined/"".
const NO_PROVIDER = "none";

const CAPABILITIES: { key: keyof AgentValues; label: string }[] = [
  { key: "streaming", label: "Streaming" },
  { key: "memory", label: "Memory" },
  { key: "rag", label: "RAG" },
  { key: "toolCalling", label: "Tool calling" },
  { key: "structuredOutput", label: "Structured output" },
  { key: "reasoning", label: "Reasoning" },
  { key: "multiAgent", label: "Multi-agent" },
];

export function RegisterAgentDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreateAgent(organizationId, projectId);
  const { data: providersData } = useProviders(organizationId, projectId, { size: 100 });
  const providers = providersData?.content ?? [];
  const {
    register,
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<AgentValues>({
    resolver: zodResolver(agentSchema),
    defaultValues: {
      visibility: "PRIVATE",
      framework: "SPRING_AI",
      language: "PYTHON",
      authType: "NONE",
    },
  });

  const providerId = watch("providerId");
  const selectedProvider = providers.find((p) => p.id === providerId);

  // Authentication is no longer agent-owned once a Provider is linked — it's inherited from the
  // Provider's own stored key (see AgentCredentialService.resolveAuthHeaders). Force the field
  // back to NONE so there is never stale/conflicting agent-level auth configuration sitting
  // alongside a provider link.
  useEffect(() => {
    if (providerId) {
      setValue("authType", "NONE");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [providerId]);

  const onSubmit = (values: AgentValues) => {
    const tags = values.tags
      ? values.tags.split(",").map((t) => t.trim()).filter(Boolean)
      : undefined;

    create.mutate(
      {
        name: values.name,
        slug: values.slug || undefined,
        description: values.description || undefined,
        endpointUrl: values.providerId ? undefined : values.endpointUrl,
        providerId: values.providerId || undefined,
        modelOverride: values.providerId ? values.modelOverride || undefined : undefined,
        endpointOverride: values.providerId ? values.endpointOverride || undefined : undefined,
        visibility: values.visibility,
        framework: values.framework,
        language: values.language,
        authType: values.authType,
        capabilities: {
          streaming: !!values.streaming,
          memory: !!values.memory,
          rag: !!values.rag,
          toolCalling: !!values.toolCalling,
          structuredOutput: !!values.structuredOutput,
          reasoning: !!values.reasoning,
          multiAgent: !!values.multiAgent,
        },
        tags,
      },
      {
        onSuccess: (agent) => {
          toast.success("Agent registered");
          setOpen(false);
          reset();
          const base = `/organizations/${organizationId}/projects/${projectId}/agents/${agent.id}`;
          // Auth-requiring agents are unusable until a credential is set, so start
          // onboarding directly on the Credentials tab. NONE agents are ready to use.
          router.push(
            values.authType === "NONE" ? base : `${base}?tab=credentials&onboarding=1`,
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
          Register agent
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Register an agent</DialogTitle>
          <DialogDescription>
            Register any AI agent — Spring AI, LangGraph, CrewAI, a custom REST service, and more.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="agent-name" error={errors.name?.message} required>
              <Input id="agent-name" placeholder="Customer Support Agent" {...register("name")} />
            </Field>
            <Field label="Slug" htmlFor="agent-slug" error={errors.slug?.message} hint="Optional">
              <Input id="agent-slug" placeholder="customer-support-agent" {...register("slug")} />
            </Field>
          </div>

          <Field
            label="Provider"
            htmlFor="agent-provider"
            hint="Optional — inherit connection details from a registered provider instead of a custom endpoint"
          >
            <Controller
              control={control}
              name="providerId"
              render={({ field }) => (
                <Select
                  value={field.value || NO_PROVIDER}
                  onValueChange={(v) => field.onChange(v === NO_PROVIDER ? "" : v)}
                >
                  <SelectTrigger id="agent-provider" onBlur={field.onBlur}>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NO_PROVIDER}>No provider (custom endpoint)</SelectItem>
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

          {providerId ? (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Model override"
                  htmlFor="agent-model-override"
                  error={errors.modelOverride?.message}
                  hint={selectedProvider?.defaultModel ? `Inherits "${selectedProvider.defaultModel}"` : "Optional"}
                >
                  <Input id="agent-model-override" placeholder="Optional" {...register("modelOverride")} />
                </Field>
                <Field
                  label="Endpoint override"
                  htmlFor="agent-endpoint-override"
                  error={errors.endpointOverride?.message}
                  hint={`Inherits "${selectedProvider?.baseUrl ?? ""}"`}
                >
                  <Input id="agent-endpoint-override" placeholder="Optional" {...register("endpointOverride")} />
                </Field>
              </div>
              {selectedProvider && <InheritedProviderPanel provider={selectedProvider} />}
            </>
          ) : (
            <Field label="Endpoint URL" htmlFor="agent-endpoint" error={errors.endpointUrl?.message} required>
              <Input
                id="agent-endpoint"
                placeholder="https://agents.example.com/support"
                {...register("endpointUrl")}
              />
            </Field>
          )}

          <Field label="Description" htmlFor="agent-description" error={errors.description?.message}>
            <Textarea id="agent-description" placeholder="What does this agent do?" {...register("description")} />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Framework" htmlFor="agent-framework" error={errors.framework?.message} required>
              <Controller
                control={control}
                name="framework"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="agent-framework" onBlur={field.onBlur}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {FRAMEWORK_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            <Field label="Language" htmlFor="agent-language" error={errors.language?.message} required>
              <Controller
                control={control}
                name="language"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="agent-language" onBlur={field.onBlur}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {LANGUAGE_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            <Field label="Visibility" htmlFor="agent-visibility" error={errors.visibility?.message} required>
              <Controller
                control={control}
                name="visibility"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="agent-visibility" onBlur={field.onBlur}>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {VISIBILITY_OPTIONS.map((o) => (
                        <SelectItem key={o.value} value={o.value}>
                          {o.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            {providerId ? (
              <Field label="Authentication" hint="No longer agent-owned once a provider is linked">
                <div className="flex h-9 items-center rounded-md border border-dashed border-border bg-muted/40 px-3 text-sm text-muted-foreground">
                  Inherited from provider ({selectedProvider?.name ?? "…"})
                </div>
              </Field>
            ) : (
              <Field label="Authentication" htmlFor="agent-auth" error={errors.authType?.message} required>
                <Controller
                  control={control}
                  name="authType"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="agent-auth" onBlur={field.onBlur}>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {AUTH_TYPE_OPTIONS.map((o) => (
                          <SelectItem key={o.value} value={o.value}>
                            {o.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                />
              </Field>
            )}
          </div>
          <p className="text-xs text-muted-foreground">
            Framework and language are descriptive metadata only — they don&apos;t affect connectivity or
            how requests are sent to the provider.
          </p>

          <Field label="Capabilities">
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {CAPABILITIES.map((cap) => (
                <label
                  key={cap.key}
                  className="flex items-center gap-2 rounded-md border border-border px-3 py-2 text-sm"
                >
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-[hsl(var(--primary))]"
                    {...register(cap.key)}
                  />
                  {cap.label}
                </label>
              ))}
            </div>
          </Field>

          <Field label="Tags" htmlFor="agent-tags" error={errors.tags?.message} hint="Comma-separated">
            <Input id="agent-tags" placeholder="production, support, tier-1" {...register("tags")} />
          </Field>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={create.isPending}>
              Register agent
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
