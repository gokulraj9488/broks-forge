"use client";

import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
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
import {
  JUDGE_FAMILY_METRIC_TYPES,
  METRIC_TYPE_OPTIONS,
  type EvaluationMetric,
  type MetricType,
} from "@/lib/api/evaluation-profiles";
import type { ProviderResponse } from "@/lib/api/providers";
import { useEmbeddingModels, useChatModels } from "@/lib/hooks/use-providers";

/**
 * One metric row's editable draft state — the single shape used by both the create dialog
 * (fresh, empty rows) and the Profile Editor page (rows hydrated from an existing profile's
 * metrics via {@link metricsToDrafts}), so the 14-metric params UI exists in exactly one place.
 */
export interface MetricDraft {
  type: MetricType;
  label: string;
  weight: string;
  threshold: string;
  // Judge-family params (SEMANTIC_SIMILARITY, LLM_JUDGE, HALLUCINATION_DETECTION, CITATION_VERIFICATION)
  providerId: string;
  model: string;
  rubric: string;
  context: string;
  // JSON_VALID
  schema: string;
  // CUSTOM
  customKey: string;
}

export function emptyMetric(type: MetricType = "EXACT_MATCH"): MetricDraft {
  return {
    type,
    label: "",
    weight: "",
    threshold: "",
    providerId: "",
    model: "",
    rubric: "",
    context: "",
    schema: "",
    customKey: "",
  };
}

export function isJudgeFamily(type: MetricType) {
  return JUDGE_FAMILY_METRIC_TYPES.includes(type);
}

/** Hydrates an existing profile's saved metrics into editable drafts (Profile Editor load path). */
export function metricsToDrafts(metrics: EvaluationMetric[]): MetricDraft[] {
  if (metrics.length === 0) {
    return [emptyMetric()];
  }
  return metrics.map((m) => {
    const params = m.params ?? {};
    return {
      type: m.type,
      label: m.label ?? "",
      weight: m.weight != null ? String(m.weight) : "",
      threshold: m.threshold != null ? String(m.threshold) : "",
      providerId: typeof params.providerId === "string" ? params.providerId : "",
      model: typeof params.model === "string" ? params.model : typeof params.embeddingModel === "string" ? params.embeddingModel : "",
      rubric: typeof params.rubric === "string" ? params.rubric : "",
      context: typeof params.context === "string" ? params.context : "",
      schema: typeof params.schema === "string" ? params.schema : "",
      customKey: typeof params.key === "string" ? params.key : "",
    };
  });
}

/** Validates drafts before submit; returns an error message, or null if valid. */
export function validateDrafts(drafts: MetricDraft[]): string | null {
  if (drafts.length === 0) {
    return "Add at least one metric";
  }
  for (const m of drafts) {
    if (isJudgeFamily(m.type) && !m.providerId) {
      return `${METRIC_TYPE_OPTIONS.find((o) => o.value === m.type)?.label} requires a provider`;
    }
    if (m.type === "CUSTOM" && !m.customKey.trim()) {
      return "Custom metric requires a key";
    }
  }
  return null;
}

/** Converts drafts into the wire payload shape (params built per metric type). */
export function draftsToPayload(drafts: MetricDraft[]): EvaluationMetric[] {
  return drafts.map((m) => {
    const params: Record<string, unknown> = {};
    if (isJudgeFamily(m.type)) {
      params.providerId = m.providerId;
      if (m.model.trim()) params.model = m.model.trim();
      if (m.type === "SEMANTIC_SIMILARITY" && m.model.trim()) params.embeddingModel = m.model.trim();
      if (m.rubric.trim()) params.rubric = m.rubric.trim();
      if (m.context.trim()) params.context = m.context.trim();
    }
    if (m.type === "JSON_VALID" && m.schema.trim()) {
      params.schema = m.schema.trim();
    }
    if (m.type === "CUSTOM") {
      params.key = m.customKey.trim();
    }
    return {
      type: m.type,
      label: m.label.trim() || undefined,
      weight: m.weight ? Number(m.weight) : undefined,
      threshold: m.threshold ? Number(m.threshold) : undefined,
      params: Object.keys(params).length > 0 ? params : undefined,
    };
  });
}

const CUSTOM_MODEL = "__custom__";

/**
 * Shared rendering for a live-discovered model field: a Select of the provider's actual models
 * plus a "Custom…" escape hatch, or the plain text field with an explanatory hint when discovery
 * isn't supported/available for this provider. Used for both the Semantic Similarity metric's
 * embedding model and the judge-family metrics' chat model — same UX, different discovery source.
 */
function DiscoveredModelField({
  label,
  ariaLabel,
  placeholder,
  providerId,
  model,
  onChange,
  data,
  isLoading,
}: {
  label: string;
  ariaLabel: string;
  placeholder: string;
  providerId: string;
  model: string;
  onChange: (model: string) => void;
  data: { supported: boolean; models: string[]; message: string | null } | undefined;
  isLoading: boolean;
}) {
  if (!providerId) {
    return (
      <Field label={label} hint="Select a provider first">
        <Input value={model} disabled placeholder={placeholder} />
      </Field>
    );
  }

  if (isLoading) {
    return (
      <Field label={label} hint="Loading available models…">
        <Input value={model} disabled />
      </Field>
    );
  }

  const models = data?.models ?? [];
  if (!data?.supported || models.length === 0) {
    return (
      <Field label={label} hint={data?.message ?? "Optional — provider default otherwise"}>
        <Input value={model} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
      </Field>
    );
  }

  const isCustomValue = model !== "" && !models.includes(model);

  return (
    <Field label={label} hint="Discovered from the selected provider">
      <Select
        value={isCustomValue ? CUSTOM_MODEL : model || undefined}
        onValueChange={(v) => onChange(v === CUSTOM_MODEL ? model : v)}
      >
        <SelectTrigger aria-label={ariaLabel}>
          <SelectValue placeholder={`Select a ${label.toLowerCase()}`} />
        </SelectTrigger>
        <SelectContent>
          {models.map((m) => (
            <SelectItem key={m} value={m}>
              {m}
            </SelectItem>
          ))}
          <SelectItem value={CUSTOM_MODEL}>Custom…</SelectItem>
        </SelectContent>
      </Select>
      {isCustomValue && (
        <Input
          className="mt-2"
          value={model}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Custom model id"
        />
      )}
    </Field>
  );
}

/** The Semantic Similarity metric's Model field — live-discovered embedding models. */
function EmbeddingModelField({
  organizationId,
  projectId,
  providerId,
  model,
  onChange,
}: {
  organizationId: string;
  projectId: string;
  providerId: string;
  model: string;
  onChange: (model: string) => void;
}) {
  const { data, isLoading } = useEmbeddingModels(organizationId, projectId, providerId || undefined);
  return (
    <DiscoveredModelField
      label="Embedding model"
      ariaLabel="Embedding model"
      placeholder="text-embedding-3-small"
      providerId={providerId}
      model={model}
      onChange={onChange}
      data={data}
      isLoading={isLoading}
    />
  );
}

/** The judge-family metrics' Model field — live-discovered chat/judge-capable models. */
function ChatModelField({
  organizationId,
  projectId,
  providerId,
  model,
  onChange,
}: {
  organizationId: string;
  projectId: string;
  providerId: string;
  model: string;
  onChange: (model: string) => void;
}) {
  const { data, isLoading } = useChatModels(organizationId, projectId, providerId || undefined);
  return (
    <DiscoveredModelField
      label="Model"
      ariaLabel="Judge model"
      placeholder="gpt-4o-mini"
      providerId={providerId}
      model={model}
      onChange={onChange}
      data={data}
      isLoading={isLoading}
    />
  );
}

export function MetricListEditor({
  metrics,
  onChange,
  providers,
  organizationId,
  projectId,
}: {
  metrics: MetricDraft[];
  onChange: (metrics: MetricDraft[]) => void;
  providers: ProviderResponse[];
  organizationId: string;
  projectId: string;
}) {
  const updateMetric = (index: number, patch: Partial<MetricDraft>) =>
    onChange(metrics.map((m, i) => (i === index ? { ...m, ...patch } : m)));

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium">Metrics</p>
        <Button type="button" size="sm" variant="outline" onClick={() => onChange([...metrics, emptyMetric()])}>
          <Plus className="h-4 w-4" />
          Add metric
        </Button>
      </div>

      {metrics.map((metric, i) => (
        <div key={i} className="space-y-3 rounded-lg border border-border p-3">
          <div className="flex items-start gap-2">
            <div className="grid flex-1 gap-3 sm:grid-cols-2">
              <Field label="Type">
                <Select value={metric.type} onValueChange={(v) => updateMetric(i, { type: v as MetricType })}>
                  <SelectTrigger aria-label="Metric type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {METRIC_TYPE_OPTIONS.map((o) => (
                      <SelectItem key={o.value} value={o.value}>
                        {o.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field label="Label" hint="Optional">
                <Input
                  value={metric.label}
                  onChange={(e) => updateMetric(i, { label: e.target.value })}
                  placeholder="Exact answer"
                />
              </Field>
              <Field label="Weight" hint="Optional">
                <Input
                  type="number"
                  step="0.1"
                  value={metric.weight}
                  onChange={(e) => updateMetric(i, { weight: e.target.value })}
                  placeholder="1"
                />
              </Field>
              <Field label="Threshold" hint="Optional">
                <Input
                  type="number"
                  step="0.05"
                  value={metric.threshold}
                  onChange={(e) => updateMetric(i, { threshold: e.target.value })}
                  placeholder="0.8"
                />
              </Field>
            </div>
            <Button
              type="button"
              size="icon"
              variant="ghost"
              className="mt-6 shrink-0 text-muted-foreground hover:text-destructive"
              onClick={() => onChange(metrics.filter((_, idx) => idx !== i))}
              disabled={metrics.length === 1}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            {METRIC_TYPE_OPTIONS.find((o) => o.value === metric.type)?.description}
          </p>

          {isJudgeFamily(metric.type) && (
            <div className="grid gap-3 rounded-md border border-dashed border-border bg-muted/30 p-3 sm:grid-cols-2">
              <Field label="Provider" required hint="The judge/embedding provider to call">
                <Select value={metric.providerId} onValueChange={(v) => updateMetric(i, { providerId: v })}>
                  <SelectTrigger aria-label="Judge provider">
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
              </Field>
              {metric.type === "SEMANTIC_SIMILARITY" ? (
                <EmbeddingModelField
                  organizationId={organizationId}
                  projectId={projectId}
                  providerId={metric.providerId}
                  model={metric.model}
                  onChange={(model) => updateMetric(i, { model })}
                />
              ) : (
                <ChatModelField
                  organizationId={organizationId}
                  projectId={projectId}
                  providerId={metric.providerId}
                  model={metric.model}
                  onChange={(model) => updateMetric(i, { model })}
                />
              )}
              {metric.type !== "SEMANTIC_SIMILARITY" && (
                <>
                  <Field label="Rubric" hint="Optional — overrides the default rubric">
                    <Textarea value={metric.rubric} onChange={(e) => updateMetric(i, { rubric: e.target.value })} rows={2} />
                  </Field>
                  <Field label="Context source" hint="Optional — falls back to the dataset's expected output">
                    <Textarea value={metric.context} onChange={(e) => updateMetric(i, { context: e.target.value })} rows={2} />
                  </Field>
                </>
              )}
            </div>
          )}

          {metric.type === "JSON_VALID" && (
            <div className="rounded-md border border-dashed border-border bg-muted/30 p-3">
              <Field label="JSON Schema" hint="Optional — validates output against this schema, not just structural JSON validity">
                <Textarea
                  value={metric.schema}
                  onChange={(e) => updateMetric(i, { schema: e.target.value })}
                  rows={4}
                  placeholder='{"type": "object", "required": ["answer"]}'
                  className="font-mono text-xs"
                />
              </Field>
            </div>
          )}

          {metric.type === "CUSTOM" && (
            <div className="rounded-md border border-dashed border-border bg-muted/30 p-3">
              <Field label="Custom metric key" required hint="Matches a registered CustomMetricEvaluator, e.g. numeric-range">
                <Input
                  value={metric.customKey}
                  onChange={(e) => updateMetric(i, { customKey: e.target.value })}
                  placeholder="numeric-range"
                />
              </Field>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
