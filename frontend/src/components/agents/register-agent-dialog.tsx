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
import { Select } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useCreateAgent } from "@/lib/hooks/use-agents";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  AUTH_TYPE_OPTIONS,
  FRAMEWORK_OPTIONS,
  LANGUAGE_OPTIONS,
  VISIBILITY_OPTIONS,
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
  const {
    register,
    handleSubmit,
    reset,
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

  const onSubmit = (values: AgentValues) => {
    const tags = values.tags
      ? values.tags.split(",").map((t) => t.trim()).filter(Boolean)
      : undefined;

    create.mutate(
      {
        name: values.name,
        slug: values.slug || undefined,
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
        onSuccess: (agent) => {
          toast.success("Agent registered");
          setOpen(false);
          reset();
          router.push(`/organizations/${organizationId}/projects/${projectId}/agents/${agent.id}`);
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

          <Field label="Endpoint URL" htmlFor="agent-endpoint" error={errors.endpointUrl?.message} required>
            <Input id="agent-endpoint" placeholder="https://agents.example.com/support" {...register("endpointUrl")} />
          </Field>

          <Field label="Description" htmlFor="agent-description" error={errors.description?.message}>
            <Textarea id="agent-description" placeholder="What does this agent do?" {...register("description")} />
          </Field>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Framework" htmlFor="agent-framework" error={errors.framework?.message} required>
              <Select id="agent-framework" {...register("framework")}>
                {FRAMEWORK_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Language" htmlFor="agent-language" error={errors.language?.message} required>
              <Select id="agent-language" {...register("language")}>
                {LANGUAGE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Visibility" htmlFor="agent-visibility" error={errors.visibility?.message} required>
              <Select id="agent-visibility" {...register("visibility")}>
                {VISIBILITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Authentication" htmlFor="agent-auth" error={errors.authType?.message} required>
              <Select id="agent-auth" {...register("authType")}>
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
