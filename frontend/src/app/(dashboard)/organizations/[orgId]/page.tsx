"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Building2 } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { TabsBar } from "@/components/ui/tabs-bar";
import { RoleBadge } from "@/components/common/badges";
import { MembersPanel } from "@/components/organizations/members-panel";
import { ProjectsPanel } from "@/components/projects/projects-panel";
import { OrganizationSettingsPanel } from "@/components/organizations/organization-settings-panel";
import { useOrganization } from "@/lib/hooks/use-organizations";

type Tab = "projects" | "members" | "settings";

const TABS = [
  { key: "projects" as const, label: "Projects" },
  { key: "members" as const, label: "Members" },
  { key: "settings" as const, label: "Settings" },
];

export default function OrganizationDetailPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = params.orgId;
  const { data: organization, isLoading, isError } = useOrganization(orgId);
  const [tab, setTab] = useState<Tab>("projects");

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (isError || !organization) {
    return (
      <EmptyState
        icon={Building2}
        title="Organization not found"
        description="It may have been deleted or you no longer have access."
      />
    );
  }

  const canManage =
    organization.currentUserRole === "OWNER" || organization.currentUserRole === "ADMIN";
  const isOwner = organization.currentUserRole === "OWNER";

  return (
    <div className="space-y-6">
      <Link
        href="/organizations"
        className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Organizations
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary/10">
            <Building2 className="h-6 w-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">{organization.name}</h1>
            <p className="text-sm text-muted-foreground">
              /{organization.slug} · {organization.memberCount} member
              {organization.memberCount === 1 ? "" : "s"}
            </p>
          </div>
        </div>
        <RoleBadge role={organization.currentUserRole} />
      </div>

      <TabsBar tabs={TABS} value={tab} onChange={setTab} />

      <div>
        {tab === "projects" && <ProjectsPanel organizationId={orgId} canManage={canManage} />}
        {tab === "members" && <MembersPanel organizationId={orgId} canManage={canManage} />}
        {tab === "settings" && (
          <OrganizationSettingsPanel
            organization={organization}
            canEdit={canManage}
            isOwner={isOwner}
          />
        )}
      </div>
    </div>
  );
}
