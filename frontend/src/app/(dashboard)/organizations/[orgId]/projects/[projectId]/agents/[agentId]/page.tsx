"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Bot, ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { HealthBadge, StatusBadge } from "@/components/common/badges";
import { VersionsPanel } from "@/components/agents/versions-panel";
import { HealthPanel } from "@/components/agents/health-panel";
import { CredentialsPanel } from "@/components/agents/credentials-panel";
import { AgentSettingsPanel } from "@/components/agents/agent-settings-panel";
import { useAgent } from "@/lib/hooks/use-agents";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { FRAMEWORK_OPTIONS, type AgentResponse } from "@/lib/api/agents";
import { formatDateTime } from "@/lib/utils";

type Tab = "overview" | "versions" | "health" | "credentials" | "settings";

const TABS = [
  { key: "overview" as const, label: "Overview" },
  { key: "versions" as const, label: "Versions" },
  { key: "health" as const, label: "Health" },
  { key: "credentials" as const, label: "Credentials" },
  { key: "settings" as const, label: "Settings" },
];

const FRAMEWORK_LABELS = Object.fromEntries(FRAMEWORK_OPTIONS.map((o) => [o.value, o.label]));

const CAPABILITY_LABELS: { key: keyof AgentResponse["capabilities"]; label: string }[] = [
  { key: "streaming", label: "Streaming" },
  { key: "memory", label: "Memory" },
  { key: "rag", label: "RAG" },
  { key: "toolCalling", label: "Tool calling" },
  { key: "structuredOutput", label: "Structured output" },
  { key: "reasoning", label: "Reasoning" },
  { key: "multiAgent", label: "Multi-agent" },
];

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <div className="text-sm">{children}</div>
    </div>
  );
}

function Overview({ agent }: { agent: AgentResponse }) {
  const enabledCaps = CAPABILITY_LABELS.filter((c) => agent.capabilities[c.key]);
  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="grid gap-6 p-6 sm:grid-cols-2">
          <Detail label="Framework">{FRAMEWORK_LABELS[agent.framework] ?? agent.framework}</Detail>
          <Detail label="Language">{agent.language}</Detail>
          <Detail label="Visibility">{agent.visibility}</Detail>
          <Detail label="Authentication">{agent.authType.replace(/_/g, " ")}</Detail>
          <Detail label="Endpoint">
            <a
              href={agent.endpointUrl}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1 break-all text-primary hover:underline"
            >
              {agent.endpointUrl}
              <ExternalLink className="h-3 w-3 shrink-0" />
            </a>
          </Detail>
          <Detail label="Active version">
            {agent.currentActiveVersionId ? (
              <span className="font-mono text-xs">{agent.currentActiveVersionId.slice(0, 8)}</span>
            ) : (
              <span className="text-muted-foreground">None active</span>
            )}
          </Detail>
          <Detail label="Last health check">{formatDateTime(agent.lastHealthCheckAt)}</Detail>
          <Detail label="Updated">{formatDateTime(agent.updatedAt)}</Detail>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-3 p-6">
          <p className="text-xs uppercase tracking-wide text-muted-foreground">Capabilities</p>
          {enabledCaps.length === 0 ? (
            <p className="text-sm text-muted-foreground">No capabilities declared.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {enabledCaps.map((c) => (
                <Badge key={c.key} variant="secondary">
                  {c.label}
                </Badge>
              ))}
            </div>
          )}
          {agent.tags.length > 0 && (
            <div className="flex flex-wrap gap-2 pt-2">
              {agent.tags.map((tag) => (
                <span key={tag} className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                  #{tag}
                </span>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

export default function AgentDetailPage() {
  const params = useParams<{ orgId: string; projectId: string; agentId: string }>();
  const { orgId, projectId, agentId } = params;
  const { data: organization } = useOrganization(orgId);
  const { data: agent, isLoading, isError } = useAgent(orgId, projectId, agentId);
  const [tab, setTab] = useState<Tab>("overview");

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";
  const canAdmin = role === "OWNER" || role === "ADMIN";

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-12 w-72" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !agent) {
    return (
      <EmptyState
        icon={Bot}
        title="Agent not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  return (
    <div className="space-y-6">
      <Link
        href={`/organizations/${orgId}/projects/${projectId}`}
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Back to project
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <Bot className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{agent.name}</h1>
            <p className="text-sm text-muted-foreground">/{agent.slug}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {agent.status === "ARCHIVED" && <StatusBadge status="ARCHIVED" />}
          <HealthBadge status={agent.healthStatus} />
        </div>
      </div>

      {agent.description && <p className="max-w-2xl text-sm text-muted-foreground">{agent.description}</p>}

      <TabsBar tabs={TABS} value={tab} onChange={setTab} />

      <div>
        {tab === "overview" && <Overview agent={agent} />}
        {tab === "versions" && (
          <VersionsPanel
            organizationId={orgId}
            projectId={projectId}
            agentId={agentId}
            canManage={canManage}
          />
        )}
        {tab === "health" && (
          <HealthPanel
            organizationId={orgId}
            projectId={projectId}
            agentId={agentId}
            disabled={agent.status === "ARCHIVED"}
          />
        )}
        {tab === "credentials" && (
          <CredentialsPanel
            organizationId={orgId}
            projectId={projectId}
            agentId={agentId}
            canManage={canAdmin}
          />
        )}
        {tab === "settings" && (
          <AgentSettingsPanel
            agent={agent}
            organizationId={orgId}
            projectId={projectId}
            canManage={canManage}
            canDelete={canAdmin}
          />
        )}
      </div>
    </div>
  );
}
