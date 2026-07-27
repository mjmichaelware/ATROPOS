import { SpecGraphApiClient } from "@/lib/api/client";
import type { OperationResponse, PageResult, SourceDocument, SourceUploadIntent, SourceUploadStatus, DocumentProvenance, FreeformRecord } from "./schemas";

export function listDocuments(client: SpecGraphApiClient, projectId: string, page?: { limit?: number; cursor?: string }) {
  return client.request<{ items: SourceDocument[] }>({ path: `/v1/projects/${projectId}/documents`, page });
}

export function getSourceWorkspace(client: SpecGraphApiClient, projectId: string) {
  return client.request<FreeformRecord>({ path: `/v1/projects/${projectId}/source-workspace` });
}

export function getDocument(client: SpecGraphApiClient, documentId: string) {
  return client.request<SourceDocument>({ path: `/v1/documents/${documentId}` });
}

export function getDocumentProvenance(client: SpecGraphApiClient, documentId: string) {
  return client.request<DocumentProvenance>({ path: `/v1/documents/${documentId}/provenance` });
}

export function listDocumentAtoms(client: SpecGraphApiClient, documentId: string, page?: { limit?: number; cursor?: string }) {
  return client.request<{ items: FreeformRecord[] }>({ path: `/v1/documents/${documentId}/atoms`, page });
}

export function createUploadIntent(
  client: SpecGraphApiClient,
  projectId: string,
  input: { filename: string; media_type: string; byte_size: number; sha256: string },
  idempotencyKey: string,
) {
  return client.request<SourceUploadIntent>({
    method: "POST",
    path: `/v1/projects/${projectId}/source-uploads`,
    body: input,
    idempotencyKey,
  });
}

export function getUploadStatus(client: SpecGraphApiClient, uploadId: string) {
  return client.request<SourceUploadStatus>({ path: `/v1/source-uploads/${uploadId}` });
}

export function finalizeUpload(client: SpecGraphApiClient, uploadId: string, idempotencyKey: string) {
  return client.request<OperationResponse>({
    method: "POST",
    path: `/v1/source-uploads/${uploadId}/finalize`,
    idempotencyKey,
  });
}

export function extractDocument(client: SpecGraphApiClient, documentId: string, idempotencyKey: string) {
  return client.request<OperationResponse>({
    method: "POST",
    path: `/v1/documents/${documentId}/extract`,
    idempotencyKey,
  });
}

export type DocumentPage = PageResult<SourceDocument>;
