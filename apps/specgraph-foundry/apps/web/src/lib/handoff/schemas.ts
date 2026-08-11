import type { ApiResult } from "@/lib/api/client";
import type { OperationLike } from "@/lib/api/operations";

export type FreeformRecord = Record<string, unknown>;

export type Binding = FreeformRecord & {
  id: string;
  system_name?: string;
  binding_type?: string;
  config?: FreeformRecord;
  enabled?: boolean;
  created_at?: string;
  etag?: string;
};

export type ArtifactSummary = FreeformRecord & {
  name: string;
  media_type: string;
  byte_length: number;
  sha256: string;
};

export type ArtifactManifestSummary = FreeformRecord & {
  id: string;
  state?: "GENERATED" | "STORED" | "VERIFIED" | "INVALID";
  manifest_version?: string;
  aggregate_sha256?: string;
  total_bytes?: number;
  artifact_count?: number;
  artifacts_preview?: ArtifactSummary[];
};

export type ExportRecord = FreeformRecord & {
  id: string;
  plan_version_id?: string;
  status?: string;
  export_type?: string;
  created_at?: string;
  verified_at?: string;
  artifact_manifest?: ArtifactManifestSummary | null;
  detail_route?: string;
};

export type ExportDownloadArtifact = ArtifactSummary & {
  signed_download_url: string;
  expires_at: string;
};

export type ExportDownloadResponse = {
  export_id: string;
  manifest_id: string;
  expires_in: number;
  artifacts: ExportDownloadArtifact[];
};

export type ExecutionRunSummary = FreeformRecord & {
  id: string;
  plan_version_id?: string;
  runtime_system?: string;
  runtime_run_id?: string;
  status?: string;
  created_at?: string;
  started_at?: string;
  completed_at?: string;
  verified_at?: string;
  detail_route?: string;
};

export type ProviderRecord = FreeformRecord & {
  id: string;
  name?: string;
  provider_class?: string;
  cost_class?: string;
  territories?: string[];
  priority?: number;
  enabled?: boolean;
  status?: string;
};

export type RendererRecord = FreeformRecord & {
  id: string;
  name?: string;
  renderer_type?: string;
  territories?: string[];
  priority?: number;
  enabled?: boolean;
};

export type RoutingPolicyRecord = FreeformRecord & {
  allow_offline_degraded?: boolean;
  paid_emergency_enabled?: boolean;
  max_paid_decisions_per_unlock?: number;
};

export type HandoffCounts = FreeformRecord & {
  bindings?: number;
  enabled_bindings?: number;
  exports?: number;
  verified_exports?: number;
  invalid_exports?: number;
  execution_runs?: number;
  verified_execution_runs?: number;
  rejected_execution_runs?: number;
  receipts?: number;
  execution_findings?: number;
  providers?: number;
  ready_providers?: number;
  renderers?: number;
  enabled_renderers?: number;
};

export type HandoffWorkspace = FreeformRecord & {
  project?: FreeformRecord;
  counts?: HandoffCounts;
  bindings?: Binding[];
  bindings_count?: number;
  bindings_has_more?: boolean;
  bindings_route?: string;
  exports?: ExportRecord[];
  exports_count?: number;
  exports_has_more?: boolean;
  exports_route?: string;
  execution_runs?: ExecutionRunSummary[];
  execution_runs_count?: number;
  execution_runs_has_more?: boolean;
  execution_runs_route?: string;
  providers?: ProviderRecord[];
  providers_count?: number;
  providers_has_more?: boolean;
  providers_route?: string;
  renderers?: RendererRecord[];
  renderers_count?: number;
  renderers_has_more?: boolean;
  renderers_route?: string;
  routing_policy?: RoutingPolicyRecord | null;
  latest_export?: ExportRecord | null;
  latest_execution_run?: ExecutionRunSummary | null;
};

export type BindingInput = {
  system_name: string;
  binding_type: string;
  config: FreeformRecord;
  enabled?: boolean;
};

export type ExecutionRunStartInput = {
  runtime_system: string;
  runtime_run_id: string;
  export_id?: string;
};

export type PageResult<T> = ApiResult<{ items: T[] }>;
export type OperationResult = ApiResult<{ operation: OperationLike & FreeformRecord }>;
