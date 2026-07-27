import { useQuery } from "@tanstack/react-query";
import { createProjectApiClient } from "@/lib/projects/api";
import { queryKeys } from "@/lib/query/keys";
import { getRouteDecision, getRoutingPolicy, listProjectProviders, listProjectRenderers } from "./api";

export function useRoutingPolicy(projectId: string) {
  return useQuery({
    queryKey: queryKeys.routingPolicy(projectId),
    queryFn: () => getRoutingPolicy(createProjectApiClient(), projectId),
  });
}

export function useProjectProviders(projectId: string) {
  return useQuery({
    queryKey: queryKeys.providers(projectId),
    queryFn: () => listProjectProviders(createProjectApiClient(), projectId),
  });
}

export function useProjectRenderers(projectId: string) {
  return useQuery({
    queryKey: queryKeys.renderers(projectId),
    queryFn: () => listProjectRenderers(createProjectApiClient(), projectId),
  });
}

export function useRouteDecisionLookup(decisionId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.routeDecisionDetail(decisionId ?? "none"),
    queryFn: () => getRouteDecision(createProjectApiClient(), decisionId as string),
    enabled: Boolean(decisionId),
  });
}
