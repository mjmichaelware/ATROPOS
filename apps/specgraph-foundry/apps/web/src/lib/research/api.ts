import { SpecGraphApiClient } from "@/lib/api/client";
import type { ConclusionInput, EvidenceInput, FreeformRecord, OperationResponse, ResearchTask, ResearchWorkspace } from "./schemas";

export function getResearchWorkspace(client: SpecGraphApiClient, projectId: string) {
  return client.request<ResearchWorkspace>({ path: `/v1/projects/${projectId}/research-workspace` });
}

export function getGapMatrix(client: SpecGraphApiClient, projectId: string) {
  return client.request<FreeformRecord>({ path: `/v1/projects/${projectId}/gap-matrix` });
}

export function listResearchTasks(client: SpecGraphApiClient, projectId: string, page?: { limit?: number; cursor?: string }) {
  return client.request<{ items: ResearchTask[] }>({ path: `/v1/projects/${projectId}/research-tasks`, page });
}

export function getResearchTask(client: SpecGraphApiClient, taskId: string) {
  return client.request<ResearchTask>({ path: `/v1/research-tasks/${taskId}` });
}

export function claimResearchTask(client: SpecGraphApiClient, projectId: string, workerId: string, idempotencyKey: string) {
  return client.request<{ task: ResearchTask }>({
    method: "POST",
    path: `/v1/projects/${projectId}/research-tasks/claim`,
    body: { worker_id: workerId, lease_seconds: 300 },
    idempotencyKey,
  });
}

export function heartbeatResearchTask(client: SpecGraphApiClient, taskId: string, workerId: string) {
  return client.request<ResearchTask>({
    method: "POST",
    path: `/v1/research-tasks/${taskId}/heartbeat`,
    body: { worker_id: workerId, lease_seconds: 300 },
  });
}

export function addResearchEvidence(client: SpecGraphApiClient, taskId: string, workerId: string, input: EvidenceInput, idempotencyKey: string) {
  return client.request<FreeformRecord>({
    method: "POST",
    path: `/v1/research-tasks/${taskId}/evidence`,
    body: { worker_id: workerId, ...input },
    idempotencyKey,
  });
}

export function completeResearchTask(client: SpecGraphApiClient, taskId: string, workerId: string, input: ConclusionInput, idempotencyKey: string) {
  return client.request<OperationResponse>({
    method: "POST",
    path: `/v1/research-tasks/${taskId}/complete`,
    body: { worker_id: workerId, ...input },
    idempotencyKey,
  });
}

export function failResearchTask(client: SpecGraphApiClient, taskId: string, workerId: string, errorMessage: string, retryable = true) {
  return client.request<ResearchTask>({
    method: "POST",
    path: `/v1/research-tasks/${taskId}/fail`,
    body: { worker_id: workerId, error_message: errorMessage, retryable },
  });
}
