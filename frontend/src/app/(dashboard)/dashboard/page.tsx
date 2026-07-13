"use client";

import Link from "next/link";
import {
  ArrowRight,
  Bot,
  Building2,
  Crown,
  Database,
  FlaskConical,
  Gauge,
  Plug,
  Rocket,
  Trophy,
  Users,
} from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { RoleBadge, HealthBadge } from "@/components/common/badges";
import { JobStatusBadge } from "@/components/common/eval-badges";
import { PageHeader } from "@/components/layout/page-header";
import { useAuth } from "@/lib/hooks/use-auth";
import { useOrganizations } from "@/lib/hooks/use-organizations";
import { useProjects } from "@/lib/hooks/use-projects";
import { useEvaluationJobs } from "@/lib/hooks/use-evaluation-jobs";
import { useBenchmarks } from "@/lib/hooks/use-benchmarks";
import { useAgents } from "@/lib/hooks/use-agents";
import { useProviders } from "@/lib/hooks/use-providers";
import { useAnalytics } from "@/lib/hooks/use-analytics";

function StatCard({
  label,
  value,
  icon: Icon,
  loading,
}: {
  label: string;
  value: number | string;
  icon: typeof Building2;
  loading?: boolean;
}) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between p-6">
        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">{label}</p>
          {loading ? (
            <Skeleton className="h-8 w-12" />
          ) : (
            <p className="text-3xl font-semibold tracking-tight">{value}</p>
          )}
        </div>
        <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10">
          <Icon className="h-5 w-5 text-primary" />
        </div>
      </CardContent>
    </Card>
  );
}

const QUICK_ACTIONS = [
  { href: "/evaluations", label: "Run evaluation", icon: FlaskConical },
  { href: "/benchmarks", label: "Import benchmark", icon: Trophy },
  { href: "/datasets", label: "Create dataset", icon: Database },
  { href: "/agents", label: "Create agent", icon: Bot },
];

export default function DashboardPage() {
  const { user } = useAuth();
  const { data: orgsData, isLoading: orgsLoading } = useOrganizations({ size: 5 });

  const organizations = orgsData?.content ?? [];
  const totalOrgs = orgsData?.totalElements ?? 0;
  const ownedOrgs = organizations.filter((o) => o.currentUserRole === "OWNER").length;
  const totalMembers = organizations.reduce((sum, o) => sum + o.memberCount, 0);

  // The dashboard has no explicit org/project selector, so the "workspace pulse" widgets below
  // use the first organization + its first project as the active workspace — same convention as
  // WorkspaceSelector defaults elsewhere. Gracefully omitted below if either doesn't exist yet.
  const activeOrgId = organizations[0]?.id;
  const { data: projectsData } = useProjects(activeOrgId, { size: 1 });
  const activeProjectId = projectsData?.content?.[0]?.id;
  const hasWorkspace = !!activeOrgId && !!activeProjectId;

  const { data: jobsData, isLoading: jobsLoading } = useEvaluationJobs(activeOrgId, activeProjectId, {
    size: 5,
  });
  const { data: benchmarksData, isLoading: benchmarksLoading } = useBenchmarks(activeOrgId, activeProjectId, {
    size: 5,
  });
  const { data: agentsData, isLoading: agentsLoading } = useAgents(activeOrgId, activeProjectId, { size: 5 });
  const { data: providersData, isLoading: providersLoading } = useProviders(activeOrgId, activeProjectId, {
    size: 20,
  });
  const { data: analyticsData, isLoading: analyticsLoading } = useAnalytics(activeOrgId, activeProjectId, 30);

  const greeting = user?.firstName ? `Welcome back, ${user.firstName}` : "Welcome back";
  const healthyProviders = (providersData?.content ?? []).filter((p) => p.healthStatus === "HEALTHY").length;
  const totalProviders = providersData?.content?.length ?? 0;

  return (
    <div>
      <PageHeader title={greeting} description="Here's what's happening across your workspace." />

      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Organizations" value={totalOrgs} icon={Building2} loading={orgsLoading} />
        <StatCard label="Owned by you" value={ownedOrgs} icon={Crown} loading={orgsLoading} />
        <StatCard label="Members (top 5 orgs)" value={totalMembers} icon={Users} loading={orgsLoading} />
      </div>

      <div className="mt-8">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">Quick actions</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {QUICK_ACTIONS.map((action) => (
            <Link key={action.href} href={action.href}>
              <Card className="h-full transition-colors hover:border-primary/40">
                <CardContent className="flex items-center gap-3 p-4">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                    <action.icon className="h-4 w-4 text-primary" />
                  </div>
                  <p className="text-sm font-medium">{action.label}</p>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      </div>

      {hasWorkspace && (
        <div className="mt-8 grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
              <CardTitle className="text-base">Recent evaluations</CardTitle>
              <Button asChild variant="ghost" size="sm">
                <Link href="/evaluations">
                  View all
                  <ArrowRight className="ml-1 h-4 w-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent className="space-y-2 pt-0">
              {jobsLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : (jobsData?.content?.length ?? 0) === 0 ? (
                <EmptyState icon={FlaskConical} title="No evaluations yet" description="Run your first evaluation." />
              ) : (
                jobsData!.content.map((job) => (
                  <Link
                    key={job.id}
                    href={`/organizations/${activeOrgId}/projects/${activeProjectId}/evaluations/${job.id}`}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm transition-colors hover:bg-accent/60"
                  >
                    <span className="truncate">{job.name}</span>
                    <JobStatusBadge status={job.status} />
                  </Link>
                ))
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
              <CardTitle className="text-base">Latest benchmarks</CardTitle>
              <Button asChild variant="ghost" size="sm">
                <Link href="/benchmarks">
                  View all
                  <ArrowRight className="ml-1 h-4 w-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent className="space-y-2 pt-0">
              {benchmarksLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : (benchmarksData?.content?.length ?? 0) === 0 ? (
                <EmptyState icon={Trophy} title="No benchmarks yet" description="Import a template from the Gallery." />
              ) : (
                benchmarksData!.content.map((b) => (
                  <Link
                    key={b.id}
                    href={`/organizations/${activeOrgId}/projects/${activeProjectId}/benchmarks/${b.id}`}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm transition-colors hover:bg-accent/60"
                  >
                    <span className="truncate">{b.name}</span>
                    <Badge variant="muted">{b.entryCount} entries</Badge>
                  </Link>
                ))
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
              <CardTitle className="text-base">Latest agents</CardTitle>
              <Button asChild variant="ghost" size="sm">
                <Link href="/agents">
                  View all
                  <ArrowRight className="ml-1 h-4 w-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent className="space-y-2 pt-0">
              {agentsLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : (agentsData?.content?.length ?? 0) === 0 ? (
                <EmptyState icon={Bot} title="No agents yet" description="Register your first AI agent." />
              ) : (
                agentsData!.content.map((agent) => (
                  <Link
                    key={agent.id}
                    href={`/organizations/${activeOrgId}/projects/${activeProjectId}/agents/${agent.id}`}
                    className="flex items-center justify-between rounded-md px-2 py-2 text-sm transition-colors hover:bg-accent/60"
                  >
                    <span className="truncate">{agent.name}</span>
                    <HealthBadge status={agent.healthStatus} />
                  </Link>
                ))
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-3">
              <CardTitle className="text-base">Provider health</CardTitle>
              <Button asChild variant="ghost" size="sm">
                <Link href="/providers">
                  View all
                  <ArrowRight className="ml-1 h-4 w-4" />
                </Link>
              </Button>
            </CardHeader>
            <CardContent className="pt-0">
              {providersLoading ? (
                <Skeleton className="h-24 w-full" />
              ) : totalProviders === 0 ? (
                <EmptyState icon={Plug} title="No providers yet" description="Register an LLM provider." />
              ) : (
                <div className="flex items-center gap-4">
                  <div className="flex h-14 w-14 items-center justify-center rounded-full bg-success/10">
                    <p className="text-lg font-semibold text-success">
                      {healthyProviders}/{totalProviders}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-sm font-medium">Providers healthy</p>
                    <p className="text-xs text-muted-foreground">
                      Based on the most recent health/connection check per provider.
                    </p>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">Evaluation success rate</CardTitle>
            </CardHeader>
            <CardContent className="pt-0">
              {analyticsLoading ? (
                <Skeleton className="h-16 w-full" />
              ) : !analyticsData || analyticsData.runCount === 0 ? (
                <EmptyState icon={Gauge} title="No runs in the last 30 days" />
              ) : (
                <div className="flex items-center gap-4">
                  <p className="text-3xl font-semibold tracking-tight">
                    {Math.round(analyticsData.passRate * 100)}%
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {analyticsData.runCount} runs across {analyticsData.jobCount} jobs, last{" "}
                    {analyticsData.windowDays} days
                  </p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card className="border-dashed">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base text-muted-foreground">
                <Rocket className="h-4 w-4" />
                Recent deployments
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-0">
              <p className="text-sm text-muted-foreground">
                Deployment tracking is planned for a future release — see the Roadmap.
              </p>
            </CardContent>
          </Card>
        </div>
      )}

      <div className="mt-8">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Your organizations</h2>
          <Button asChild variant="ghost" size="sm">
            <Link href="/organizations">
              View all
              <ArrowRight className="ml-1 h-4 w-4" />
            </Link>
          </Button>
        </div>

        {orgsLoading ? (
          <div className="grid gap-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-20 w-full" />
            ))}
          </div>
        ) : organizations.length === 0 ? (
          <EmptyState
            icon={Building2}
            title="No organizations yet"
            description="Create your first organization to start building."
            action={
              <Button asChild>
                <Link href="/organizations">Create organization</Link>
              </Button>
            }
          />
        ) : (
          <div className="grid gap-3">
            {organizations.map((org) => (
              <Link key={org.id} href={`/organizations/${org.id}`}>
                <Card className="transition-colors hover:border-primary/40">
                  <CardHeader className="flex flex-row items-center justify-between space-y-0">
                    <div className="space-y-1">
                      <CardTitle className="text-base">{org.name}</CardTitle>
                      <p className="text-xs text-muted-foreground">
                        {org.memberCount} member{org.memberCount === 1 ? "" : "s"} · /{org.slug}
                      </p>
                    </div>
                    <RoleBadge role={org.currentUserRole} />
                  </CardHeader>
                </Card>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
