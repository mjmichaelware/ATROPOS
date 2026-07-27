import { SpecGraphApiClient } from "@/lib/api/client";
import type { PlanSynthesizeResponse, PlanVerifyResponse, RelationCreateResponse, RelationInput } from "./schemas";

export function createProjectRelation(client: SpecGraphApiClient, projectId: string, input: RelationInput) {
  return client.request<RelationCreateResponse>({ method: "POST", path: `/v1/projects/${projectId}/relations`, body: input });
}

export function synthesizePlan(client: SpecGraphApiClient, projectId: string, allowOpenResearch: boolean, idempotencyKey: string) {
  return client.request<PlanSynthesizeResponse>({
    method: "POST",
    path: `/v1/projects/${projectId}/plans`,
    body: { allow_open_research: allowOpenResearch },
    idempotencyKey,
  });
}

export function verifyPlan(client: SpecGraphApiClient, planId: string, idempotencyKey: string) {
  return client.request<PlanVerifyResponse>({ method: "POST", path: `/v1/plans/${planId}/verify`, idempotencyKey });
}
