"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, FileText, Lightbulb } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { StatusBadge } from "@/components/common/badges";
import { PromptVersionsPanel } from "@/components/prompts/prompt-versions-panel";
import { PromptComparePanel } from "@/components/prompts/prompt-compare-panel";
import { PromptSettingsPanel } from "@/components/prompts/prompt-settings-panel";
import { AdvisoryReport } from "@/components/advisor/advisory-report";
import { usePrompt } from "@/lib/hooks/use-prompts";
import { usePromptAdvisory } from "@/lib/hooks/use-advisor";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { formatDateTime } from "@/lib/utils";
import type { PromptResponse } from "@/lib/api/prompts";

type Tab = "overview" | "versions" | "advisor" | "compare" | "settings";

const TABS = [
  { key: "overview" as const, label: "Overview" },
  { key: "versions" as const, label: "Versions" },
  { key: "advisor" as const, label: "Advisor" },
  { key: "compare" as const, label: "Compare" },
  { key: "settings" as const, label: "Settings" },
];

function PromptAdvisor({
  organizationId,
  projectId,
  promptId,
}: {
  organizationId: string;
  projectId: string;
  promptId: string;
}) {
  const { data, isLoading, isError } = usePromptAdvisory(organizationId, projectId, promptId);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-40 w-full" />
        ))}
      </div>
    );
  }

  if (isError || !data) {
    return (
      <EmptyState icon={Lightbulb} title="Couldn't load advisory" description="Please try again." />
    );
  }

  return <AdvisoryReport report={data} />;
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm">{children}</div>
    </div>
  );
}

function Overview({ prompt }: { prompt: PromptResponse }) {
  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="grid gap-6 p-6 sm:grid-cols-2">
          <Detail label="Status">{prompt.status}</Detail>
          <Detail label="Latest version">v{prompt.latestVersionNumber ?? 0}</Detail>
          <Detail label="Active version">
            {prompt.currentActiveVersionId ? (
              <span className="font-mono text-xs">{prompt.currentActiveVersionId.slice(0, 8)}</span>
            ) : (
              <span className="text-muted-foreground">None active</span>
            )}
          </Detail>
          <Detail label="Created">{formatDateTime(prompt.createdAt)}</Detail>
          <Detail label="Updated">{formatDateTime(prompt.updatedAt)}</Detail>
        </CardContent>
      </Card>
      {prompt.tags.length > 0 && (
        <Card>
          <CardContent className="space-y-2 p-6">
            <p className="text-xs uppercase tracking-wide text-muted-foreground">Tags</p>
            <div className="flex flex-wrap gap-2">
              {prompt.tags.map((tag) => (
                <span key={tag} className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  #{tag}
                </span>
              ))}
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

export default function PromptDetailPage() {
  const params = useParams<{ orgId: string; projectId: string; promptId: string }>();
  const { orgId, projectId, promptId } = params;
  const { data: organization } = useOrganization(orgId);
  const { data: prompt, isLoading, isError } = usePrompt(orgId, projectId, promptId);
  const [tab, setTab] = useState<Tab>("overview");

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";
  const canDelete = role === "OWNER" || role === "ADMIN";

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-12 w-72" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !prompt) {
    return (
      <EmptyState
        icon={FileText}
        title="Prompt not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href="/prompts"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to prompts
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <FileText className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{prompt.name}</h1>
            <p className="text-sm text-muted-foreground">/{prompt.slug}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {prompt.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
          <Badge variant="outline">v{prompt.latestVersionNumber ?? 0}</Badge>
        </div>
      </div>

      {prompt.description && (
        <p className="max-w-2xl text-sm text-muted-foreground">{prompt.description}</p>
      )}

      <TabsBar tabs={TABS} value={tab} onChange={setTab} />

      <div>
        {tab === "overview" && <Overview prompt={prompt} />}
        {tab === "versions" && (
          <PromptVersionsPanel
            organizationId={orgId}
            projectId={projectId}
            promptId={promptId}
            canManage={canManage && prompt.status !== "ARCHIVED"}
          />
        )}
        {tab === "advisor" && (
          <PromptAdvisor organizationId={orgId} projectId={projectId} promptId={promptId} />
        )}
        {tab === "compare" && (
          <PromptComparePanel organizationId={orgId} projectId={projectId} promptId={promptId} />
        )}
        {tab === "settings" && (
          <PromptSettingsPanel
            prompt={prompt}
            organizationId={orgId}
            projectId={projectId}
            canManage={canManage}
            canDelete={canDelete}
          />
        )}
      </div>
    </div>
  );
}
