"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
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
import {
  useArchiveAgent,
  useDeleteAgent,
  useUpdateAgent,
} from "@/lib/hooks/use-agents";
import { useProviders } from "@/lib/hooks/use-providers";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  AUTH_TYPE_OPTIONS,
  FRAMEWORK_OPTIONS,
  LANGUAGE_OPTIONS,
  VISIBILITY_OPTIONS,
  type AgentResponse,
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

export function AgentSettingsPanel({
  agent,
  organizationId,
  projectId,
  canManage,
  canDelete,
}: {
  agent: AgentResponse;
  organizationId: string;
  projectId: string;
  canManage: boolean;
  canDelete: boolean;
}) {
  const router = useRouter();
  const update = useUpdateAgent(organizationId, projectId, agent.id);
  const archive = useArchiveAgent(organizationId, projectId);
  const remove = useDeleteAgent(organizationId, projectId);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const archived = agent.status === "ARCHIVED";
  const editable = canManage && !archived;
  const { data: providersData } = useProviders(organizationId, projectId, { size: 100 });
  const providers = providersData?.content ?? [];

  const {
    register,
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isDirty },
  } = useForm<AgentValues>({
    resolver: zodResolver(agentSchema),
    defaultValues: {
      name: agent.name,
      description: agent.description ?? "",
      endpointUrl: agent.endpointUrl,
      providerId: agent.providerId ?? "",
      modelOverride: agent.modelOverride ?? "",
      endpointOverride: agent.endpointOverride ?? "",
      visibility: agent.visibility,
      framework: agent.framework,
      language: agent.language,
      authType: agent.authType,
      streaming: agent.capabilities.streaming,
      memory: agent.capabilities.memory,
      rag: agent.capabilities.rag,
      toolCalling: agent.capabilities.toolCalling,
      structuredOutput: agent.capabilities.structuredOutput,
      reasoning: agent.capabilities.reasoning,
      multiAgent: agent.capabilities.multiAgent,
      tags: agent.tags.join(", "),
    },
  });

  const providerId = watch("providerId");
  const selectedProvider = providers.find((p) => p.id === providerId);

  // Authentication is inherited from the linked Provider, not agent-owned — see
  // RegisterAgentDialog for the same rule applied at creation time.
  useEffect(() => {
    if (providerId) {
      setValue("authType", "NONE");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [providerId]);

  const onSubmit = (values: AgentValues) => {
    const tags = values.tags
      ? values.tags.split(",").map((t) => t.trim()).filter(Boolean)
      : [];
    update.mutate(
      {
        name: values.name,
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
        onSuccess: () => toast.success("Agent updated"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const toggleArchive = () => {
    archive.mutate(
      { agentId: agent.id, archive: !archived },
      {
        onSuccess: () => toast.success(archived ? "Agent unarchived" : "Agent archived"),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const handleDelete = () => {
    remove.mutate(agent.id, {
      onSuccess: () => {
        toast.success("Agent deleted");
        router.replace(`/organizations/${organizationId}/projects/${projectId}`);
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
          <CardTitle className="text-base">Configuration</CardTitle>
          <CardDescription>
            {archived
              ? "This agent is archived and read-only. Unarchive it to make changes."
              : "Update this agent's metadata and capabilities."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <Field label="Name" htmlFor="set-name" error={errors.name?.message} required>
              <Input id="set-name" disabled={!editable} {...register("name")} />
            </Field>
            <Field label="Slug" hint="The slug is permanent and cannot be changed.">
              <Input value={agent.slug} disabled readOnly />
            </Field>
            <Field
              label="Provider"
              htmlFor="set-provider"
              hint="Optional — inherit connection details from a registered provider instead of a custom endpoint"
            >
              <Controller
                control={control}
                name="providerId"
                render={({ field }) => (
                  <Select
                    value={field.value || NO_PROVIDER}
                    onValueChange={(v) => field.onChange(v === NO_PROVIDER ? "" : v)}
                    disabled={!editable}
                  >
                    <SelectTrigger id="set-provider" onBlur={field.onBlur}>
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
                    htmlFor="set-model-override"
                    error={errors.modelOverride?.message}
                    hint={selectedProvider?.defaultModel ? `Inherits "${selectedProvider.defaultModel}"` : "Optional"}
                  >
                    <Input id="set-model-override" disabled={!editable} {...register("modelOverride")} />
                  </Field>
                  <Field
                    label="Endpoint override"
                    htmlFor="set-endpoint-override"
                    error={errors.endpointOverride?.message}
                    hint={`Inherits "${selectedProvider?.baseUrl ?? ""}"`}
                  >
                    <Input id="set-endpoint-override" disabled={!editable} {...register("endpointOverride")} />
                  </Field>
                </div>
                {selectedProvider && <InheritedProviderPanel provider={selectedProvider} />}
              </>
            ) : (
              <Field label="Endpoint URL" htmlFor="set-endpoint" error={errors.endpointUrl?.message} required>
                <Input id="set-endpoint" disabled={!editable} {...register("endpointUrl")} />
              </Field>
            )}
            <Field label="Description" htmlFor="set-description" error={errors.description?.message}>
              <Textarea id="set-description" disabled={!editable} {...register("description")} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Framework" htmlFor="set-framework">
                <Controller
                  control={control}
                  name="framework"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="set-framework" disabled={!editable} onBlur={field.onBlur}>
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
              <Field label="Language" htmlFor="set-language">
                <Controller
                  control={control}
                  name="language"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="set-language" disabled={!editable} onBlur={field.onBlur}>
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
              <Field label="Visibility" htmlFor="set-visibility">
                <Controller
                  control={control}
                  name="visibility"
                  render={({ field }) => (
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id="set-visibility" disabled={!editable} onBlur={field.onBlur}>
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
                <Field label="Authentication" htmlFor="set-auth">
                  <Controller
                    control={control}
                    name="authType"
                    render={({ field }) => (
                      <Select value={field.value} onValueChange={field.onChange}>
                        <SelectTrigger id="set-auth" disabled={!editable} onBlur={field.onBlur}>
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
                      disabled={!editable}
                      {...register(cap.key)}
                    />
                    {cap.label}
                  </label>
                ))}
              </div>
            </Field>
            <Field label="Tags" htmlFor="set-tags" error={errors.tags?.message} hint="Comma-separated">
              <Input id="set-tags" disabled={!editable} {...register("tags")} />
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
            <CardDescription>Archive to make the agent read-only, or delete it permanently.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-3">
            <Button variant="outline" onClick={toggleArchive} loading={archive.isPending}>
              {archived ? "Unarchive agent" : "Archive agent"}
            </Button>
            {canDelete && (
              <Button variant="destructive" onClick={() => setConfirmDelete(true)}>
                Delete agent
              </Button>
            )}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Delete agent?"
        description={`"${agent.name}", its versions, credentials and health history will be permanently removed.`}
        confirmLabel="Delete agent"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
