import { SpecGraphApiClient } from "@/lib/api/client";
import type { Binding, BindingInput, ExecutionRunStartInput, ExportDownloadResponse, ExportRecord, HandoffWorkspace, OperationResult } from "./schemas";

export function getHandoffWorkspace(client: SpecGraphApiClient, projectId: string) {
  return client.request<HandoffWorkspace>({ path: `/v1/projects/${projectId}/handoff-workspace` });
}

export function listProjectBindings(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: Binding[] }>({ path: `/v1/projects/${projectId}/bindings` });
}

export function createOrUpdateBinding(client: SpecGraphApiClient, projectId: string, input: BindingInput, idempotencyKey: string, ifMatch?: string) {
  return client.request<Binding>({ method: "POST", path: `/v1/projects/${projectId}/bindings`, body: input, idempotencyKey, ifMatch });
}

export function listProjectExports(client: SpecGraphApiClient, projectId: string) {
  return client.request<{ items: ExportRecord[] }>({ path: `/v1/projects/${projectId}/exports` });
}

export function exportPlan(client: SpecGraphApiClient, planId: string, outputRoot: string | undefined, idempotencyKey: string) {
  return client.request<OperationResult["body"]>({
    method: "POST",
    path: `/v1/plans/${planId}/exports`,
    body: outputRoot ? { output_root: outputRoot } : {},
    idempotencyKey,
  });
}

export function getExport(client: SpecGraphApiClient, exportId: string) {
  return client.request<ExportRecord>({ path: `/v1/exports/${exportId}` });
}

export function verifyExport(client: SpecGraphApiClient, exportId: string, idempotencyKey: string) {
  return client.request<OperationResult["body"]>({ method: "POST", path: `/v1/exports/${exportId}/verify`, idempotencyKey });
}

export function downloadExportArtifacts(client: SpecGraphApiClient, exportId: string) {
  return client.request<ExportDownloadResponse>({ path: `/v1/exports/${exportId}/download` });
}

export function startExecutionRun(client: SpecGraphApiClient, planId: string, input: ExecutionRunStartInput, idempotencyKey: string) {
  return client.request<OperationResult["body"]>({ method: "POST", path: `/v1/plans/${planId}/execution-runs`, body: input, idempotencyKey });
}
