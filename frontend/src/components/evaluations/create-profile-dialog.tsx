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
import { Textarea } from "@/components/ui/textarea";
import { useCreateEvaluationProfile } from "@/lib/hooks/use-evaluation-profiles";
import { getApiErrorMessage } from "@/lib/api/client";
import { PRESET_OPTIONS, presetMetrics, type ProfilePreset } from "@/lib/api/evaluation-profiles";
import { draftsToPayload, emptyMetric, type MetricDraft } from "@/components/evaluations/metric-list-editor";

export function CreateProfileDialog({
  organizationId,
  projectId,
}: {
  organizationId: string;
  projectId: string;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const create = useCreateEvaluationProfile(organizationId, projectId);

  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [description, setDescription] = useState("");
  const [preset, setPreset] = useState<ProfilePreset>("CONVERSATION");
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setName("");
    setSlug("");
    setDescription("");
    setPreset("CONVERSATION");
    setError(null);
  };

  const submit = () => {
    setError(null);
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    const drafts: MetricDraft[] = presetMetrics(preset).map((type) => emptyMetric(type));

    create.mutate(
      {
        name: name.trim(),
        slug: slug.trim() || undefined,
        description: description.trim() || undefined,
        metrics: draftsToPayload(drafts),
      },
      {
        onSuccess: (profile) => {
          toast.success("Profile created — configure providers/models for judge metrics next");
          setOpen(false);
          resetForm();
          router.push(`/organizations/${organizationId}/projects/${projectId}/evaluations/profiles/${profile.id}`);
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
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Create an evaluation profile</DialogTitle>
          <DialogDescription>
            Pick a starting preset — you&apos;ll configure providers/models and fine-tune metrics on the next screen.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="ep-name" required>
              <Input id="ep-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Customer Support Conversation" />
            </Field>
            <Field label="Slug" htmlFor="ep-slug" hint="Optional">
              <Input id="ep-slug" value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="customer-support-conversation" />
            </Field>
          </div>
          <Field label="Description" htmlFor="ep-desc">
            <Textarea id="ep-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>

          <Field label="Preset" hint="A starting point — fully editable afterward">
            <div className="grid gap-2 sm:grid-cols-2">
              {PRESET_OPTIONS.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  onClick={() => setPreset(o.value)}
                  className={`rounded-lg border p-3 text-left text-sm transition-colors ${
                    preset === o.value
                      ? "border-primary bg-primary/5"
                      : "border-border hover:bg-muted/40"
                  }`}
                >
                  <p className="font-medium">{o.label}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{o.description}</p>
                </button>
              ))}
            </div>
          </Field>

          {error && <p className="text-xs font-medium text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => setOpen(false)}>
            Cancel
          </Button>
          <Button type="button" onClick={submit} loading={create.isPending}>
            Create & configure
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
