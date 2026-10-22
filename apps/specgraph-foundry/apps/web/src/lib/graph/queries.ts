import { useQuery } from "@tanstack/react-query";
import { getProject } from "@/lib/projects/api";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { getPlan, getPlanningWorkspace, listProjectRelations } from "./api";

export function useGraphProject(projectId: string) {
  return useQuery({
    queryKey: queryKeys.project(projectId),
    queryFn: () => getProject(createProjectApiClient(), projectId),
  });
}

export function usePlanningWorkspace(projectId: string) {
  return useQuery({
    queryKey: queryKeys.planning(projectId),
    queryFn: () => getPlanningWorkspace(createProjectApiClient(), projectId),
  });
}

export function useAuthorityRelations(projectId: string, enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.graphRelations(projectId),
    queryFn: () => listProjectRelations(createProjectApiClient(), projectId, { limit: 200 }),
    enabled,
  });
}

export function usePlanGraph(planId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.graphPlan(planId ?? "none"),
    queryFn: () => getPlan(createProjectApiClient(), planId as string),
    enabled: Boolean(planId),
  });
}
