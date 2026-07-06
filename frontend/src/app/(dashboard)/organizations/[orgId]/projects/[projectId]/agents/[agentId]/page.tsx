"use client";

import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { ArrowLeft, Bot, ExternalLink, Lightbulb } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar, type TabItem } from "@/components/ui/tabs-bar";
import { HealthBadge, StatusBadge } from "@/components/common/badges";
import { VersionsPanel } from "@/components/agents/versions-panel";
import { HealthPanel } from "@/components/agents/health-panel";
import { CredentialsPanel } from "@/components/agents/credentials-panel";
import { AgentSettingsPanel } from "@/components/agents/agent-settings-panel";
import {
  AgentReadinessBadge,
  AgentReadinessChecklist,
  CredentialSetupAlert,
} from "@/components/agents/agent-onboarding";
import { AdvisoryReport } from "@/components/advisor/advisory-report";
import {
  useAgent,
  useAgentCredentials,
  useAgentHealth,
  useRunHealthCheck,
  useTestCredential,
} from "@/lib/hooks/use-agents";
import { useAgentAdvisory } from "@/lib/hooks/use-advisor";
import { useOrganization } from "@/lib/hooks/use-organizations";
import { FRAMEWORK_OPTIONS, type AgentCredentialResponse, type AgentResponse } from "@/lib/api/agents";
import { computeAgentReadiness } from "@/lib/agent-readiness";
import { formatDateTime } from "@/lib/utils";

type Tab = "overview" | "versions" | "advisor" | "health" | "credentials" | "settings";

const TABS: TabItem<Tab>[] = [
  { key: "overview", label: "Overview" },
  { key: "versions", label: "Versions" },
  { key: "advisor", label: "Advisor" },
  { key: "health", label: "Health" },
  { key: "credentials", label: "Credentials" },
  { key: "settings", label: "Settings" },
];
const TAB_KEYS = TABS.map((t) => t.key);

function coerceTab(value: string | null): Tab {
  return value && (TAB_KEYS as string[]).includes(value) ? (value as Tab) : "overview";
}

function AgentAdvisor({
  organizationId,
  projectId,
  agentId,
}: {
  organizationId: string;
  projectId: string;
  agentId: string;
}) {
  const { data, isLoading, isError } = useAgentAdvisory(organizationId, projectId, agentId);

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

const SETUP_DOT = (
  <>
    <span className="inline-block h-1.5 w-1.5 rounded-full bg-warning" aria-hidden />
    <span className="sr-only">setup required</span>
  </>
);

function AgentDetail() {
  const params = useParams<{ orgId: string; projectId: string; agentId: string }>();
  const searchParams = useSearchParams();
  const { orgId, projectId, agentId } = params;

  const { data: organization } = useOrganization(orgId);
  const { data: agent, isLoading, isError } = useAgent(orgId, projectId, agentId);

  const role = organization?.currentUserRole;
  const canManage = role === "OWNER" || role === "ADMIN" || role === "MEMBER";
  const canAdmin = role === "OWNER" || role === "ADMIN";

  // Health is readable by any member; credentials are admin-only, so only fetch
  // them when the viewer can manage them (otherwise fall back to the agent's flag).
  const { data: health } = useAgentHealth(orgId, projectId, agentId);
  const { data: credentials } = useAgentCredentials(
    orgId,
    projectId,
    canAdmin ? agentId : undefined,
  );
  const runHealthCheck = useRunHealthCheck(orgId, projectId, agentId);
  const testCredential = useTestCredential(orgId, projectId, agentId);

  const [tab, setTab] = useState<Tab>(() => coerceTab(searchParams.get("tab")));
  const [onboarding] = useState(() => searchParams.get("onboarding") === "1");
  const [credDialogOpen, setCredDialogOpen] = useState(false);
  const autoOpenedRef = useRef(false);
  const readyToastRef = useRef(false);

  const readiness = agent ? computeAgentReadiness(agent, credentials, health) : null;
  const needsCredentialSetup = readiness?.needsCredentialSetup ?? false;
  const activeCredentialId = credentials?.find((c) => c.active)?.id;

  const goToCredentials = useCallback(() => {
    setTab("credentials");
    setCredDialogOpen(true);
  }, []);

  const handleCredentialSaved = (saved: AgentCredentialResponse) => {
    setCredDialogOpen(false);
    setTab("health");
    // Persist a connection test so "Connection verified" can complete on its own,
    // then the Health panel auto-runs the first check — a hands-free hand-off.
    testCredential.mutate(saved.id);
  };

  const verifyConnection = () => {
    if (activeCredentialId) testCredential.mutate(activeCredentialId);
  };

  const runHealth = () => {
    setTab("health");
    runHealthCheck.mutate();
  };

  // Onboarding: land on Credentials and open the dialog once, only when setup is needed.
  useEffect(() => {
    if (!onboarding || autoOpenedRef.current || !canAdmin || !readiness) return;
    if (readiness.needsCredentialSetup) {
      autoOpenedRef.current = true;
      setTab("credentials");
      setCredDialogOpen(true);
    }
  }, [onboarding, canAdmin, readiness]);

  // Celebrate reaching Ready during onboarding (once).
  useEffect(() => {
    if (onboarding && readiness?.isReady && !readyToastRef.current) {
      readyToastRef.current = true;
      toast.success("Agent is ready — onboarding complete");
    }
  }, [onboarding, readiness?.isReady]);

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

  const showChecklist = !!readiness?.requiresCredential && !readiness.isReady;
  const tabs: TabItem<Tab>[] = TABS.map((t) =>
    t.key === "credentials" && needsCredentialSetup ? { ...t, badge: SETUP_DOT } : t,
  );

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
          {readiness?.requiresCredential && <AgentReadinessBadge readiness={readiness} />}
          <HealthBadge status={agent.healthStatus} />
        </div>
      </div>

      {agent.description && <p className="max-w-2xl text-sm text-muted-foreground">{agent.description}</p>}

      {showChecklist && readiness && (
        <AgentReadinessChecklist
          readiness={readiness}
          onConfigureCredential={goToCredentials}
          onVerifyConnection={verifyConnection}
          onRunHealth={runHealth}
          runningHealth={runHealthCheck.isPending}
          verifyingConnection={testCredential.isPending}
        />
      )}

      <TabsBar tabs={tabs} value={tab} onChange={setTab} />

      <div>
        {tab === "overview" && (
          <div className="space-y-6">
            {needsCredentialSetup && <CredentialSetupAlert onConfigure={goToCredentials} />}
            <Overview agent={agent} />
          </div>
        )}
        {tab === "versions" && (
          <VersionsPanel
            organizationId={orgId}
            projectId={projectId}
            agentId={agentId}
            canManage={canManage}
          />
        )}
        {tab === "advisor" &&
          (needsCredentialSetup ? (
            <div className="space-y-4">
              <CredentialSetupAlert onConfigure={goToCredentials} />
              <EmptyState
                icon={Lightbulb}
                title="Advisor unavailable"
                description="Configure credentials so the platform can reach this agent, then the engineering advisor will be available."
              />
            </div>
          ) : (
            <AgentAdvisor organizationId={orgId} projectId={projectId} agentId={agentId} />
          ))}
        {tab === "health" && (
          <HealthPanel
            organizationId={orgId}
            projectId={projectId}
            agentId={agentId}
            disabled={agent.status === "ARCHIVED"}
            needsCredentialSetup={needsCredentialSetup}
            onConfigureCredential={goToCredentials}
            autoRun={onboarding && !needsCredentialSetup}
          />
        )}
        {tab === "credentials" && (
          <CredentialsPanel
            organizationId={orgId}
            projectId={projectId}
            agentId={agentId}
            canManage={canAdmin}
            createOpen={credDialogOpen}
            onCreateOpenChange={setCredDialogOpen}
            onCredentialSaved={handleCredentialSaved}
            initialAuthType={agent.authType}
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

export default function AgentDetailPage() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <AgentDetail />
    </Suspense>
  );
}
