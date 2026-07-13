"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { benchmarkGalleryApi, type ProvisionGalleryBenchmarkPayload } from "@/lib/api/benchmark-gallery";

const keys = {
  templates: (o: string, p: string) => ["organizations", o, "projects", p, "benchmark-gallery", "templates"] as const,
};

export function useGalleryTemplates(organizationId: string | undefined, projectId: string | undefined) {
  return useQuery({
    queryKey: keys.templates(organizationId ?? "", projectId ?? ""),
    queryFn: () => benchmarkGalleryApi.listTemplates(organizationId as string, projectId as string),
    enabled: !!organizationId && !!projectId,
  });
}

export function useProvisionGalleryTemplate(organizationId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ProvisionGalleryBenchmarkPayload) =>
      benchmarkGalleryApi.provision(organizationId, projectId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "projects", projectId, "datasets"] });
      queryClient.invalidateQueries({ queryKey: ["organizations", organizationId, "projects", projectId, "prompts"] });
      queryClient.invalidateQueries({
        queryKey: ["organizations", organizationId, "projects", projectId, "evaluation-profiles"],
      });
      queryClient.invalidateQueries({
        queryKey: ["organizations", organizationId, "projects", projectId, "evaluation-jobs"],
      });
    },
  });
}
