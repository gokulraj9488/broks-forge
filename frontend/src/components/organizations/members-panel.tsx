"use client";

import { useState } from "react";
import { Trash2, Users } from "lucide-react";
import { toast } from "sonner";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { Select } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { RoleBadge } from "@/components/common/badges";
import { AddMemberDialog } from "@/components/organizations/add-member-dialog";
import {
  useOrganizationMembers,
  useRemoveMember,
  useUpdateMemberRole,
} from "@/lib/hooks/use-organizations";
import { useAuth } from "@/lib/hooks/use-auth";
import { getApiErrorMessage } from "@/lib/api/client";
import { formatDate, initials } from "@/lib/utils";
import type { OrganizationMemberResponse, OrganizationRole } from "@/lib/api/types";

export function MembersPanel({
  organizationId,
  canManage,
}: {
  organizationId: string;
  canManage: boolean;
}) {
  const { user } = useAuth();
  const { data, isLoading } = useOrganizationMembers(organizationId, { size: 100 });
  const updateRole = useUpdateMemberRole(organizationId);
  const removeMember = useRemoveMember(organizationId);
  const [pendingRemoval, setPendingRemoval] = useState<OrganizationMemberResponse | null>(null);

  const members = data?.content ?? [];

  const handleRoleChange = (member: OrganizationMemberResponse, role: OrganizationRole) => {
    updateRole.mutate(
      { userId: member.userId, role },
      {
        onSuccess: () => toast.success(`${member.fullName || member.email} is now ${role.toLowerCase()}`),
        onError: (error) => toast.error(getApiErrorMessage(error)),
      },
    );
  };

  const handleRemove = () => {
    if (!pendingRemoval) return;
    removeMember.mutate(pendingRemoval.userId, {
      onSuccess: () => {
        toast.success("Member removed");
        setPendingRemoval(null);
      },
      onError: (error) => {
        toast.error(getApiErrorMessage(error));
        setPendingRemoval(null);
      },
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          {members.length} member{members.length === 1 ? "" : "s"}
        </p>
        {canManage && <AddMemberDialog organizationId={organizationId} />}
      </div>

      {members.length === 0 ? (
        <EmptyState icon={Users} title="No members" />
      ) : (
        <Card>
          <CardContent className="divide-y divide-border p-0">
            {members.map((member) => {
              const isSelf = member.userId === user?.id;
              const isOwner = member.role === "OWNER";
              return (
                <div key={member.id} className="flex items-center justify-between gap-3 p-4">
                  <div className="flex min-w-0 items-center gap-3">
                    <Avatar>
                      <AvatarFallback>{initials(member.fullName || member.email)}</AvatarFallback>
                    </Avatar>
                    <div className="min-w-0">
                      <p className="flex items-center gap-2 truncate text-sm font-medium">
                        {member.fullName || member.email}
                        {isSelf && (
                          <Badge variant="outline" className="px-1.5 py-0 text-[10px]">
                            You
                          </Badge>
                        )}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">
                        {member.email} · joined {formatDate(member.joinedAt)}
                      </p>
                    </div>
                  </div>

                  <div className="flex shrink-0 items-center gap-2">
                    {canManage && !isOwner ? (
                      <Select
                        value={member.role}
                        onChange={(e) =>
                          handleRoleChange(member, e.target.value as OrganizationRole)
                        }
                        className="h-8 w-28 text-xs"
                        disabled={updateRole.isPending}
                      >
                        <option value="MEMBER">Member</option>
                        <option value="ADMIN">Admin</option>
                      </Select>
                    ) : (
                      <RoleBadge role={member.role} />
                    )}
                    {canManage && !isOwner && (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                        onClick={() => setPendingRemoval(member)}
                        aria-label="Remove member"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>
      )}

      <ConfirmDialog
        open={!!pendingRemoval}
        onOpenChange={(open) => !open && setPendingRemoval(null)}
        title="Remove member?"
        description={`${pendingRemoval?.fullName || pendingRemoval?.email} will lose access to this organization.`}
        confirmLabel="Remove"
        destructive
        loading={removeMember.isPending}
        onConfirm={handleRemove}
      />
    </div>
  );
}
