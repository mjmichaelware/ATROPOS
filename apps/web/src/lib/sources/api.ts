import { SpecGraphApiClient } from "@/lib/api/client";
import type { AtomsExportBundle, FinalizedUpload, OperationResponse, PageResult, SourceDocument, SourceUploadIntent, SourceUploadStatus, DocumentProvenance, FreeformRecord } from "./schemas";

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

// Renders the document's already-extracted atoms into a downloadable
// text/PDF file on demand - synchronous, since it's just formatting rows
// that already exist, not running extraction again. The API layer only
// ever returns JSON (see gateway.py's server-side response encoding), so
// the files come back base64-encoded in the body and get turned into a
// real device download client-side (see sources/downloads.ts).
export function exportDocumentAtoms(client: SpecGraphApiClient, documentId: string) {
  return client.request<AtomsExportBundle>({ path: `/v1/documents/${documentId}/atoms/export` });
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

export function finalizeUpload(client: SpecGraphApiClient, uploadId: string, idempotencyKey: string, rawBase64?: string) {
  // finalize_source_upload is an async operation server-side, so this
  // normally comes back as a 202 OperationResponse with a `location` to
  // poll - see the comment on FinalizedUpload in ./schemas for the
  // (rare, infra-dependent) synchronous fallback shape.
  return client.request<FinalizedUpload | OperationResponse>({
    method: "POST",
    path: `/v1/source-uploads/${uploadId}/finalize`,
    // Sending the browser's own copy of the bytes lets the server verify
    // against them directly instead of reading the object back from
    // Supabase Storage, whose authenticated download route has been
    // observed reporting a freshly-uploaded object as missing.
    body: rawBase64 !== undefined ? { raw_base64: rawBase64 } : undefined,
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
