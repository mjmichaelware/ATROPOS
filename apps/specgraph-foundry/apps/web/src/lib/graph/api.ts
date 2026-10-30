import { SpecGraphApiClient } from "@/lib/api/client";
import type { AuthorityRelation, PlanDetail, PlanningWorkspace, PlanSummary } from "./schemas";

export function getPlanningWorkspace(client: SpecGraphApiClient, projectId: string) {
  return client.request<PlanningWorkspace>({ path: `/v1/projects/${projectId}/planning-workspace` });
}

export function listProjectRelations(client: SpecGraphApiClient, projectId: string, page?: { limit?: number; cursor?: string }) {
  return client.request<{ items: AuthorityRelation[] }>({ path: `/v1/projects/${projectId}/relations`, page });
}

export function listProjectPlans(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: PlanSummary[] }>({ path: `/v1/projects/${projectId}/plans` });
}

export function getPlan(client: SpecGraphApiClient, planId: string) {
  return client.request<PlanDetail>({ path: `/v1/plans/${planId}` });
}
