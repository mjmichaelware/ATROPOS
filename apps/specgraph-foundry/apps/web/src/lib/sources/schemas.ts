import type { ApiResult } from "@/lib/api/client";
import type { OperationLike } from "@/lib/api/operations";

export type FreeformRecord = Record<string, unknown>;

export type SourceDocument = FreeformRecord & {
  id: string;
  project_id?: string;
  title?: string;
  media_type?: string;
  byte_length?: number;
  content_sha256?: string;
  sha256?: string;
  created_at?: string;
  sections_count?: number;
  chunks_count?: number;
  atoms_count?: number;
  extraction_state?: string;
};

export type SourceUploadIntent = {
  id: string;
  project_id: string;
  status: string;
  bucket: string;
  object_path: string;
  filename: string;
  media_type: string;
  byte_size: number;
  signed_upload_url: string;
  signed_url_expires_at: string;
  required_upload_headers: Record<string, string>;
};

export type SourceUploadStatus = Omit<SourceUploadIntent, "signed_upload_url" | "signed_url_expires_at" | "required_upload_headers"> & {
  failure_code: string | null;
  document_id?: string | null;
  document_route?: string | null;
  created_at: string;
  updated_at: string;
  expires_at: string;
  finalized_at: string | null;
};

export type RawAuthoritySummary = {
  source_upload_id?: string;
  bucket?: string;
  object_path?: string;
  original_media_type?: string;
  byte_count?: number;
  sha256?: string;
};

export type DerivationLocator = {
  kind?: string;
  ordinal?: number;
  label?: string;
  derived_byte_start?: number;
  derived_byte_end?: number;
  derived_line_start?: number;
  derived_line_end?: number;
};

export type DerivationSummary = {
  adapter_name?: string;
  adapter_version?: string;
  detected_media_type?: string;
  derived_byte_count?: number;
  derived_sha256?: string;
  locator_kind?: string;
  locators_preview?: DerivationLocator[];
  locators_count?: number;
  locators_has_more?: boolean;
  created_at?: string;
};

export type DocumentProvenance = FreeformRecord & {
  provenance?: {
    sha256?: string;
    byte_start?: number;
    byte_end?: number;
    line_start?: number;
    line_end?: number;
    raw_authority?: RawAuthoritySummary | null;
    derivation?: DerivationSummary | null;
  };
};

export type OperationResponse = {
  operation: OperationLike & FreeformRecord;
};

export type PageResult<T> = ApiResult<{ items: T[] }>;
