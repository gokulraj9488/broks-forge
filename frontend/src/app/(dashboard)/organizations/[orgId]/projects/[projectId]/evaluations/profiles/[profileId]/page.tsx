"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, Copy, Power, PowerOff, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Field } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  MetricListEditor,
  draftsToPayload,
  metricsToDrafts,
  validateDrafts,
  type MetricDraft,
} from "@/components/evaluations/metric-list-editor";
import {
  useDeleteEvaluationProfile,
  useDuplicateEvaluationProfile,
  useEvaluationProfile,
  useToggleEvaluationProfileEnabled,
  useUpdateEvaluationProfile,
} from "@/lib/hooks/use-evaluation-profiles";
import { useProviders } from "@/lib/hooks/use-providers";
import { getApiErrorMessage } from "@/lib/api/client";

export default function EvaluationProfileEditorPage() {
  const params = useParams<{ orgId: string; projectId: string; profileId: string }>();
  const { orgId, projectId, profileId } = params;
  const router = useRouter();

  const { data: profile, isLoading } = useEvaluationProfile(orgId, projectId, profileId);
  const { data: providersData } = useProviders(orgId, projectId, { size: 100 });
  const providers = providersData?.content ?? [];
  const update = useUpdateEvaluationProfile(orgId, projectId, profileId);
  const duplicate = useDuplicateEvaluationProfile(orgId, projectId);
  const toggleEnabled = useToggleEvaluationProfileEnabled(orgId, projectId);
  const remove = useDeleteEvaluationProfile(orgId, projectId);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [passThreshold, setPassThreshold] = useState("");
  const [metrics, setMetrics] = useState<MetricDraft[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [loadedProfileId, setLoadedProfileId] = useState<string | null>(null);

  // Hydrate local edit state once per loaded profile — never re-overwrite on background refetch
  // while the user is mid-edit (e.g. after a save, the refetched profile matches what was just
  // submitted, but we don't want an unrelated refetch to clobber unsaved local changes).
  useEffect(() => {
    if (profile && profile.id !== loadedProfileId) {
      setName(profile.name);
      setDescription(profile.description ?? "");
      setPassThreshold(profile.passThreshold != null ? String(profile.passThreshold) : "");
      setMetrics(metricsToDrafts(profile.metrics));
      setLoadedProfileId(profile.id);
    }
  }, [profile, loadedProfileId]);

  const handleSave = () => {
    setError(null);
    if (!name.trim()) {
      setError("Name is required");
      return;
    }
    const validationError = validateDrafts(metrics);
    if (validationError) {
      setError(validationError);
      return;
    }
    update.mutate(
      {
        name: name.trim(),
        description: description.trim() || undefined,
        metrics: draftsToPayload(metrics),
        passThreshold: passThreshold ? Number(passThreshold) : undefined,
      },
      {
        onSuccess: (saved) => {
          toast.success(`Saved — now version ${saved.currentVersionNumber}`);
        },
        onError: (err) => toast.error(getApiErrorMessage(err)),
      },
    );
  };

  const handleDuplicate = () => {
    duplicate.mutate(profileId, {
      onSuccess: (copy) => {
        toast.success(`Duplicated "${copy.name}"`);
        router.push(`/organizations/${orgId}/projects/${projectId}/evaluations/profiles/${copy.id}`);
      },
      onError: (err) => toast.error(getApiErrorMessage(err)),
    });
  };

  const handleToggle = () => {
    if (!profile) return;
    toggleEnabled.mutate(
      { profileId, enabled: !profile.enabled },
      {
        onSuccess: () => toast.success(profile.enabled ? "Profile disabled" : "Profile enabled"),
        onError: (err) => toast.error(getApiErrorMessage(err)),
      },
    );
  };

  const handleDelete = () => {
    remove.mutate(profileId, {
      onSuccess: () => {
        toast.success("Profile deleted");
        router.push("/evaluations");
      },
      onError: (err) => {
        toast.error(getApiErrorMessage(err));
        setDeleteOpen(false);
      },
    });
  };

  if (isLoading || !profile) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/evaluations"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to evaluations
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{profile.name}</h1>
            <Badge variant="muted">v{profile.currentVersionNumber}</Badge>
            {!profile.enabled && <Badge variant="muted">Disabled</Badge>}
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            Editing metrics/pass-threshold creates a new version — evaluation jobs already run always keep the
            version they were pinned to at creation.
          </p>
        </div>
        <div className="flex gap-2">
          <Button type="button" variant="outline" onClick={handleDuplicate} loading={duplicate.isPending}>
            <Copy className="h-4 w-4" />
            Duplicate
          </Button>
          <Button type="button" variant="outline" onClick={handleToggle} loading={toggleEnabled.isPending}>
            {profile.enabled ? <PowerOff className="h-4 w-4" /> : <Power className="h-4 w-4" />}
            {profile.enabled ? "Disable" : "Enable"}
          </Button>
          <Button
            type="button"
            variant="outline"
            className="text-destructive hover:text-destructive"
            onClick={() => setDeleteOpen(true)}
          >
            <Trash2 className="h-4 w-4" />
            Delete
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="space-y-4 p-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name" htmlFor="ep-name" required>
              <Input id="ep-name" value={name} onChange={(e) => setName(e.target.value)} />
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
          </div>
          <Field label="Description" htmlFor="ep-desc">
            <Textarea id="ep-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
          </Field>
        </CardContent>
      </Card>

      <MetricListEditor
        metrics={metrics}
        onChange={setMetrics}
        providers={providers}
        organizationId={orgId}
        projectId={projectId}
      />

      {error && <p className="text-sm font-medium text-destructive">{error}</p>}

      <div className="flex justify-end">
        <Button type="button" onClick={handleSave} loading={update.isPending}>
          Save changes
        </Button>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title={`Delete "${profile.name}"?`}
        description="This can't be undone."
        confirmLabel="Delete profile"
        destructive
        loading={remove.isPending}
        onConfirm={handleDelete}
      />
    </div>
  );
}
