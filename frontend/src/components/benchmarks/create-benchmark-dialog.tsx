"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
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
import { useCreateBenchmark } from "@/lib/hooks/use-benchmarks";
import { useEvaluationJobs } from "@/lib/hooks/use-evaluation-jobs";
import { getApiErrorMessage } from "@/lib/api/client";
import {
  BENCHMARK_TYPE_OPTIONS,
  METRIC_KEY_OPTIONS,
  type BenchmarkType,
} from "@/lib/api/benchmarks";

export function CreateBenchmarkDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreateBenchmark(organizationId, projectId);
  const { data: jobsData } = useEvaluationJobs(organizationId, projectId, {
    size: 100,
    status: "COMPLETED",
  });
  const jobs = jobsData?.content ?? [];

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [type, setType] = useState<BenchmarkType>("AGENT_VS_AGENT");
  const [metricKey, setMetricKey] = useState("passRate");
  const [selected, setSelected] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setName("");
    setDescription("");
    setType("AGENT_VS_AGENT");
    setMetricKey("passRate");
    setSelected([]);
    setError(null);
  };

  const toggle = (jobId: string) =>
    setSelected((prev) =>
      prev.includes(jobId) ? prev.filter((id) => id !== jobId) : [...prev, jobId],
    );

  const submit = () => {
    setError(null);
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    if (selected.length < 2) {
      setError("Select at least two completed evaluations to compare");
      return;
    }
    create.mutate(
      {
        name: name.trim(),
        description: description.trim() || undefined,
        type,
        metricKey,
        entries: selected.map((evaluationJobId) => ({ evaluationJobId })),
      },
      {
        onSuccess: (benchmark) => {
          toast.success("Benchmark created");
          setOpen(false);
          reset();
          router.push(
            `/organizations/${organizationId}/projects/${projectId}/benchmarks/${benchmark.id}`,
          );
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
        if (!o) reset();
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm">
          <Plus className="h-4 w-4" />
          New benchmark
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Create a benchmark</DialogTitle>
          <DialogDescription>
            Compare completed evaluations side by side on a chosen metric.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <Field label="Name" htmlFor="bm-name" required>
            <Input id="bm-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Q3 agent shootout" />
          </Field>
          <Field label="Description" htmlFor="bm-desc">
            <Textarea id="bm-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Type" htmlFor="bm-type" required>
              <Select value={type} onValueChange={(v) => setType(v as BenchmarkType)}>
                <SelectTrigger id="bm-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {BENCHMARK_TYPE_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
            <Field label="Ranking metric" htmlFor="bm-metric">
              <Select value={metricKey} onValueChange={setMetricKey}>
                <SelectTrigger id="bm-metric">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {METRIC_KEY_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </div>

          <Field label="Evaluations to compare" hint="Pick two or more completed evaluations">
            {jobs.length === 0 ? (
              <p className="rounded-md border border-dashed border-border p-3 text-xs text-muted-foreground">
                No completed evaluations yet. Run some evaluations first.
              </p>
            ) : (
              <div className="max-h-56 space-y-1 overflow-y-auto rounded-md border border-border p-1">
                {jobs.map((job) => (
                  <label
                    key={job.id}
                    className="flex cursor-pointer items-center gap-2 rounded px-2 py-1.5 text-sm hover:bg-muted/50"
                  >
                    <input
                      type="checkbox"
                      className="h-4 w-4 accent-[hsl(var(--primary))]"
                      checked={selected.includes(job.id)}
                      onChange={() => toggle(job.id)}
                    />
                    <span className="truncate">{job.name}</span>
                  </label>
                ))}
              </div>
            )}
          </Field>

          {error && <p className="text-xs font-medium text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={submit} loading={create.isPending}>
            Create benchmark
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
