"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import {
  useArchiveAgent,
  useDeleteAgent,
  useUpdateAgent,
} from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  AUTH_TYPE_OPTIONS,
  FRAMEWORK_OPTIONS,
  LANGUAGE_OPTIONS,
  VISIBILITY_OPTIONS,
  type AgentResponse,
} from "@/lib/api/agents";
import { agentSchema, type AgentValues } from "@/lib/validations";

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

  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<AgentValues>({
    resolver: zodResolver(agentSchema),
    defaultValues: {
      name: agent.name,
      description: agent.description ?? "",
      endpointUrl: agent.endpointUrl,
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

  const onSubmit = (values: AgentValues) => {
    const tags = values.tags
      ? values.tags.split(",").map((t) => t.trim()).filter(Boolean)
      : [];
    update.mutate(
      {
        name: values.name,
        description: values.description || undefined,
        endpointUrl: values.endpointUrl,
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
            <Field label="Endpoint URL" htmlFor="set-endpoint" error={errors.endpointUrl?.message} required>
              <Input id="set-endpoint" disabled={!editable} {...register("endpointUrl")} />
            </Field>
            <Field label="Description" htmlFor="set-description" error={errors.description?.message}>
              <Textarea id="set-description" disabled={!editable} {...register("description")} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Framework" htmlFor="set-framework">
                <Select id="set-framework" disabled={!editable} {...register("framework")}>
                  {FRAMEWORK_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Language" htmlFor="set-language">
                <Select id="set-language" disabled={!editable} {...register("language")}>
                  {LANGUAGE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Visibility" htmlFor="set-visibility">
                <Select id="set-visibility" disabled={!editable} {...register("visibility")}>
                  {VISIBILITY_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="Authentication" htmlFor="set-auth">
                <Select id="set-auth" disabled={!editable} {...register("authType")}>
                  {AUTH_TYPE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
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
