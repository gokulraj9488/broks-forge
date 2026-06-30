"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { PageParams } from "@/lib/api/organizations";
import {
  datasetsApi,
  type CreateDatasetPayload,
  type CreateDatasetVersionPayload,
  type DatasetFilterParams,
  type UpdateDatasetPayload,
} from "@/lib/api/datasets";

const keys = {
  scope: (o: string, p: string) => ["organizations", o, "projects", p, "datasets"] as const,
  list: (o: string, p: string, params: DatasetFilterParams) =>
    ["organizations", o, "projects", p, "datasets", "list", params] as const,
  detail: (o: string, p: string, d: string) =>
    ["organizations", o, "projects", p, "datasets", d] as const,
  versions: (o: string, p: string, d: string) =>
    ["organizations", o, "projects", p, "datasets", d, "versions"] as const,
  items: (o: string, p: string, d: string, v: string) =>
    ["organizations", o, "projects", p, "datasets", d, "versions", v, "items"] as const,
  stats: (o: string, p: string, d: string, v: string | undefined) =>
    ["organizations", o, "projects", p, "datasets", d, "stats", v ?? "current"] as const,
};

export function useDatasets(
  organizationId: string | undefined,
  projectId: string | undefined,
  params: DatasetFilterParams = {},
) {
  return useQuery({
    queryKey: keys.list(organizationId ?? "", projectId ?? "", params),
    queryFn: () => datasetsApi.list(organizationId as string, projectId as string, params),
    enabled: !!organizationId && !!projectId,
  });
}

export function useDataset(
  organizationId: string | undefined,
  projectId: string | undefined,
  datasetId: string | undefined,
) {
  return useQuery({
    queryKey: keys.detail(organizationId ?? "", projectId ?? "", datasetId ?? ""),
    queryFn: () =>
      datasetsApi.get(organizationId as string, projectId as string, datasetId as string),
    enabled: !!organizationId && !!projectId && !!datasetId,
  });
}

export function useCreateDataset(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateDatasetPayload) =>
      datasetsApi.create(organizationId, projectId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useUpdateDataset(organizationId: string, projectId: string, datasetId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateDatasetPayload) =>
      datasetsApi.update(organizationId, projectId, datasetId, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDeleteDataset(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (datasetId: string) => datasetsApi.remove(organizationId, projectId, datasetId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useArchiveDataset(organizationId: string, projectId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ datasetId, archive }: { datasetId: string; archive: boolean }) =>
      archive
        ? datasetsApi.archive(organizationId, projectId, datasetId)
        : datasetsApi.unarchive(organizationId, projectId, datasetId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.scope(organizationId, projectId) }),
  });
}

export function useDatasetVersions(
  organizationId: string | undefined,
  projectId: string | undefined,
  datasetId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: [...keys.versions(organizationId ?? "", projectId ?? "", datasetId ?? ""), params],
    queryFn: () =>
      datasetsApi.listVersions(
        organizationId as string,
        projectId as string,
        datasetId as string,
        params,
      ),
    enabled: !!organizationId && !!projectId && !!datasetId,
  });
}

export function useCreateDatasetVersion(
  organizationId: string,
  projectId: string,
  datasetId: string,
) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateDatasetVersionPayload) =>
      datasetsApi.createVersion(organizationId, projectId, datasetId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.versions(organizationId, projectId, datasetId) });
      qc.invalidateQueries({ queryKey: keys.detail(organizationId, projectId, datasetId) });
    },
  });
}

export function useDatasetItems(
  organizationId: string | undefined,
  projectId: string | undefined,
  datasetId: string | undefined,
  versionId: string | undefined,
  params: PageParams = {},
) {
  return useQuery({
    queryKey: [
      ...keys.items(organizationId ?? "", projectId ?? "", datasetId ?? "", versionId ?? ""),
      params,
    ],
    queryFn: () =>
      datasetsApi.listItems(
        organizationId as string,
        projectId as string,
        datasetId as string,
        versionId as string,
        params,
      ),
    enabled: !!organizationId && !!projectId && !!datasetId && !!versionId,
  });
}

export function useDatasetStats(
  organizationId: string | undefined,
  projectId: string | undefined,
  datasetId: string | undefined,
  versionId?: string,
) {
  return useQuery({
    queryKey: keys.stats(organizationId ?? "", projectId ?? "", datasetId ?? "", versionId),
    queryFn: () =>
      datasetsApi.stats(
        organizationId as string,
        projectId as string,
        datasetId as string,
        versionId,
      ),
    enabled: !!organizationId && !!projectId && !!datasetId,
  });
}
