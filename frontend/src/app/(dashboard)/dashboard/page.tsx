"use client";

import Link from "next/link";
import { ArrowRight, Building2 } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { Button } from "@/components/ui/button";
import { RoleBadge } from "@/components/common/badges";
import { EngineeringBrief } from "@/components/dashboard/engineering-brief";
import { useAuth } from "@/lib/hooks/use-auth";
import { useOrganizations } from "@/lib/hooks/use-organizations";
import { useProjects } from "@/lib/hooks/use-projects";

/**
 * The Brief — Broks Forge's landing surface (Volume III §13.1).
 *
 * This page deliberately contains no counters, no widget grid and no charts above the fold. It opens with a
 * sentence about the state of the engineering system, then production risk, then the decisions that need a
 * human, then what the organization learned. Supporting navigation lives below the story, never above it
 * (P-1: meaning before measurement).
 */
export default function DashboardPage() {
  const { user } = useAuth();
  const { data: orgsData, isLoading: orgsLoading } = useOrganizations({ size: 5 });

  const organizations = orgsData?.content ?? [];

  // The brief reports on the active workspace — first organization + first project, the same convention
  // WorkspaceSelector uses elsewhere.
  const activeOrgId = organizations[0]?.id;
  const { data: projectsData } = useProjects(activeOrgId, { size: 1 });
  const activeProjectId = projectsData?.content?.[0]?.id;
  const hasWorkspace = !!activeOrgId && !!activeProjectId;

  const greeting = timeGreeting(user?.firstName);

  if (orgsLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-24 w-full rounded-xl" />
        <Skeleton className="h-48 w-full rounded-xl" />
      </div>
    );
  }

  // First contact with no organization at all — teach the model rather than apologise (L-32).
  if (organizations.length === 0) {
    return (
      <div className="space-y-6">
        <div className="space-y-2">
          <p className="text-sm text-muted-foreground">{greeting}</p>
          <h1 className="text-2xl font-semibold tracking-tight">
            Broks Forge is where your AI engineering will be recorded, explained and remembered.
          </h1>
          <p className="max-w-2xl text-sm text-muted-foreground">
            Every provider you register, prompt you promote and evaluation you run becomes a permanent
            engineering act — traceable, comparable, and connected to everything it affects. Create an
            organization to begin.
          </p>
        </div>
        <EmptyState
          icon={Building2}
          title="No organizations yet"
          description="An organization is the boundary your engineering record lives in."
          action={
            <Button asChild>
              <Link href="/organizations">Create organization</Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="space-y-10">
      {hasWorkspace ? (
        <EngineeringBrief
          organizationId={activeOrgId!}
          projectId={activeProjectId!}
          greeting={greeting}
        />
      ) : (
        <div className="space-y-4">
          <div className="space-y-2">
            <p className="text-sm text-muted-foreground">{greeting}</p>
            <h1 className="text-2xl font-semibold tracking-tight">
              Your organization has no project yet.
            </h1>
            <p className="max-w-2xl text-sm text-muted-foreground">
              Projects are where engineering happens — agents, prompts, datasets and the evaluations that judge
              them. Create one and this brief will start reporting on it.
            </p>
          </div>
          <Button asChild>
            <Link href="/projects">Create a project</Link>
          </Button>
        </div>
      )}

      {/* Supporting navigation — deliberately below the story. */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Your organizations
          </h2>
          <Button asChild variant="ghost" size="sm">
            <Link href="/organizations">
              View all
              <ArrowRight className="ml-1 h-4 w-4" />
            </Link>
          </Button>
        </div>
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
      </div>

      {/* The product states what it is (L-89) — quietly, in its own voice. */}
      <Card className="border-dashed">
        <CardContent className="p-4">
          <p className="text-xs text-muted-foreground">
            <span className="font-medium text-foreground">Broks Forge</span> is an AI Engineering Operating
            System. It records what you build, explains why it changed, and keeps the evidence behind every
            decision — so your organization remembers its engineering, not just its results.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

function timeGreeting(firstName?: string | null): string {
  const hour = new Date().getHours();
  const part = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  return firstName ? `${part}, ${firstName}.` : `${part}.`;
}
