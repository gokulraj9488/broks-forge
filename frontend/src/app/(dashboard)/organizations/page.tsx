"use client";

import Link from "next/link";
import { Building2, Users } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { RoleBadge, StatusBadge } from "@/components/common/badges";
import { PageHeader } from "@/components/layout/page-header";
import { CreateOrganizationDialog } from "@/components/organizations/create-organization-dialog";
import { useOrganizations } from "@/lib/hooks/use-organizations";

export default function OrganizationsPage() {
  const { data, isLoading, isError } = useOrganizations({ size: 50 });
  const organizations = data?.content ?? [];

  return (
    <div>
      <PageHeader
        title="Organizations"
        description="Workspaces that group your team, projects and API keys."
        action={<CreateOrganizationDialog />}
      />

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-28 w-full" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState icon={Building2} title="Couldn't load organizations" description="Please try again." />
      ) : organizations.length === 0 ? (
        <EmptyState
          icon={Building2}
          title="No organizations yet"
          description="Create your first organization to get started."
          action={<CreateOrganizationDialog />}
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {organizations.map((org) => (
            <Link key={org.id} href={`/organizations/${org.id}`} className="group">
              <Card className="h-full transition-colors group-hover:border-primary/40">
                <CardContent className="p-5">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10">
                        <Building2 className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <h3 className="font-medium leading-tight">{org.name}</h3>
                        <p className="text-xs text-muted-foreground">/{org.slug}</p>
                      </div>
                    </div>
                    <RoleBadge role={org.currentUserRole} />
                  </div>
                  {org.description && (
                    <p className="mt-3 line-clamp-2 text-sm text-muted-foreground">{org.description}</p>
                  )}
                  <div className="mt-4 flex items-center gap-4 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Users className="h-3.5 w-3.5" />
                      {org.memberCount} member{org.memberCount === 1 ? "" : "s"}
                    </span>
                    <StatusBadge status={org.status} />
                  </div>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
