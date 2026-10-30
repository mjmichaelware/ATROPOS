import { SpecGraphApiClient } from "@/lib/api/client";
import type { ExecutionRunDetail, ExecutionRunSummary, OperationResult } from "./schemas";

export function listProjectExecutionRuns(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: ExecutionRunSummary[] }>({ path: `/v1/projects/${projectId}/execution-runs` });
}

export function getExecutionRun(client: SpecGraphApiClient, runId: string) {
  return client.request<ExecutionRunDetail>({ path: `/v1/execution-runs/${runId}` });
}

export function verifyExecutionRun(client: SpecGraphApiClient, runId: string, idempotencyKey: string) {
  return client.request<OperationResult["body"]>({ method: "POST", path: `/v1/execution-runs/${runId}/verify`, idempotencyKey });
}
