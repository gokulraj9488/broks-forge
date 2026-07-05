"use client";

import { useState } from "react";
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
import { useCreateRegressionCheck } from "@/lib/hooks/use-regression";
import { useEvaluationJobs } from "@/lib/hooks/use-evaluation-jobs";
import { getApiErrorMessage } from "@/lib/api/client";

export function CreateRegressionDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const [open, setOpen] = useState(false);
  const create = useCreateRegressionCheck(organizationId, projectId);
  const { data: jobsData } = useEvaluationJobs(organizationId, projectId, {
    size: 100,
    status: "COMPLETED",
  });
  const jobs = jobsData?.content ?? [];

  const [name, setName] = useState("");
  const [baselineJobId, setBaselineJobId] = useState("");
  const [candidateJobId, setCandidateJobId] = useState("");
  const [tolerancePct, setTolerancePct] = useState("5");
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setName("");
    setBaselineJobId("");
    setCandidateJobId("");
    setTolerancePct("5");
    setError(null);
  };

  const submit = () => {
    setError(null);
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    if (!baselineJobId || !candidateJobId) {
      setError("Select both a baseline and a candidate evaluation");
      return;
    }
    if (baselineJobId === candidateJobId) {
      setError("Baseline and candidate must differ");
      return;
    }
    create.mutate(
      {
        name: name.trim(),
        baselineJobId,
        candidateJobId,
        tolerancePct: tolerancePct ? Number(tolerancePct) : undefined,
      },
      {
        onSuccess: () => {
          toast.success("Regression check created");
          setOpen(false);
          reset();
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
          New check
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Create a regression check</DialogTitle>
          <DialogDescription>
            Compare a candidate evaluation against a baseline and flag metric regressions.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <Field label="Name" htmlFor="rc-name" required>
            <Input id="rc-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="v2 vs v1 nightly" />
          </Field>
          <Field label="Baseline evaluation" htmlFor="rc-baseline" required>
            <Select value={baselineJobId === "" ? undefined : baselineJobId} onValueChange={setBaselineJobId}>
              <SelectTrigger id="rc-baseline">
                <SelectValue placeholder="Select baseline" />
              </SelectTrigger>
              <SelectContent>
                {jobs.map((j) => (
                  <SelectItem key={j.id} value={j.id}>
                    {j.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field label="Candidate evaluation" htmlFor="rc-candidate" required>
            <Select value={candidateJobId === "" ? undefined : candidateJobId} onValueChange={setCandidateJobId}>
              <SelectTrigger id="rc-candidate">
                <SelectValue placeholder="Select candidate" />
              </SelectTrigger>
              <SelectContent>
                {jobs.map((j) => (
                  <SelectItem key={j.id} value={j.id}>
                    {j.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field label="Tolerance %" htmlFor="rc-tol" hint="Allowed regression before flagging">
            <Input
              id="rc-tol"
              type="number"
              step="1"
              min="0"
              value={tolerancePct}
              onChange={(e) => setTolerancePct(e.target.value)}
            />
          </Field>
          {error && <p className="text-xs font-medium text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={submit} loading={create.isPending}>
            Create check
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
