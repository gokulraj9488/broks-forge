"use client";

import { useState } from "react";
import { Plus, Trash2 } from "lucide-react";
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
import { useCreateEvaluationProfile } from "@/lib/hooks/use-evaluation-profiles";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  METRIC_TYPE_OPTIONS,
  type EvaluationMetric,
  type MetricType,
} from "@/lib/api/evaluation-profiles";

interface MetricDraft {
  type: MetricType;
  label: string;
  weight: string;
  threshold: string;
}

const emptyMetric = (): MetricDraft => ({ type: "EXACT_MATCH", label: "", weight: "", threshold: "" });

export function CreateProfileDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const [open, setOpen] = useState(false);
  const create = useCreateEvaluationProfile(organizationId, projectId);

  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const [passThreshold, setPassThreshold] = useState("");
  const [metrics, setMetrics] = useState<MetricDraft[]>([emptyMetric()]);
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setName("");
    setSlug("");
    setDescription("");
    setPassThreshold("");
    setMetrics([emptyMetric()]);
    setError(null);
  };

  const updateMetric = (index: number, patch: Partial<MetricDraft>) =>
    setMetrics((prev) => prev.map((m, i) => (i === index ? { ...m, ...patch } : m)));

  const submit = () => {
    setError(null);
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    if (metrics.length === 0) {
      setError("Add at least one metric");
      return;
    }
    const payloadMetrics: EvaluationMetric[] = metrics.map((m) => ({
      type: m.type,
      label: m.label.trim() || undefined,
      weight: m.weight ? Number(m.weight) : undefined,
      threshold: m.threshold ? Number(m.threshold) : undefined,
    }));

    create.mutate(
      {
        name: name.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
        metrics: payloadMetrics,
        passThreshold: passThreshold ? Number(passThreshold) : undefined,
      },
      {
        onSuccess: () => {
          toast.success("Profile created");
          setOpen(false);
          resetForm();
        },
        onError: (err) => toast.error(getApiErrorMessage(err)),
      },
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        setOpen(o);
        if (!o) resetForm();
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="h-4 w-4" />
          New profile
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create an evaluation profile</DialogTitle>
          <DialogDescription>
            Define the metrics that score each run, plus an overall pass threshold.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="ep-name" required>
              <Input id="ep-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Strict QA" />
            </Field>
            <Field label="Slug" htmlFor="ep-slug" hint="Optional">
              <Input id="ep-slug" value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="strict-qa" />
            </Field>
          </div>
          <Field label="Description" htmlFor="ep-desc">
            <Textarea id="ep-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <Field
            label="Pass threshold"
            htmlFor="ep-threshold"
            hint="Overall weighted score (0–1) required to pass a run"
          >
            <Input
              id="ep-threshold"
              type="number"
              step="0.05"
              min="0"
              max="1"
              value={passThreshold}
              onChange={(e) => setPassThreshold(e.target.value)}
              placeholder="0.7"
            />
          </Field>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-sm font-medium">Metrics</p>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => setMetrics((prev) => [...prev, emptyMetric()])}
              >
                <Plus className="h-4 w-4" />
                Add metric
              </Button>
            </div>

            {metrics.map((metric, i) => (
              <div key={i} className="space-y-3 rounded-lg border border-border p-3">
                <div className="flex items-start gap-2">
                  <div className="grid flex-1 gap-3 sm:grid-cols-2">
                    <Field label="Type">
                      <Select
                        value={metric.type}
                        onValueChange={(v) => updateMetric(i, { type: v as MetricType })}
                      >
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
                    onClick={() => setMetrics((prev) => prev.filter((_, idx) => idx !== i))}
                    disabled={metrics.length === 1}
                  >
                    <Trash2 className="h-4 w-4" />
                  </Button>
                </div>
                <p className="text-xs text-muted-foreground">
                  {METRIC_TYPE_OPTIONS.find((o) => o.value === metric.type)?.description}
                </p>
              </div>
            ))}
          </div>

          {error && <p className="text-xs font-medium text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={submit} loading={create.isPending}>
            Create profile
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
