import type { ApiResult } from "@/lib/api/client";
import type { OperationLike } from "@/lib/api/operations";

export type FreeformRecord = Record<string, unknown>;

export type ExecutionRunNode = FreeformRecord & {
  id: string;
  run_id?: string;
  graph_node_id?: string;
  atom_id?: string;
  stage?: string;
  sequence_number?: number;
  title?: string;
  status?: string;
  lease_owner?: string | null;
  lease_expires_at?: string | null;
  attempt_count?: number;
  accepted_receipt_id?: string | null;
  created_at?: string;
  updated_at?: string;
};

export type ExecutionAttempt = FreeformRecord & {
  id: string;
  run_node_id?: string;
  worker_id?: string;
  status?: string;
  lease_expires_at?: string;
  started_at?: string;
  completed_at?: string | null;
  error_message?: string | null;
};

/**
 * Safe receipt metadata only. The server also returns a raw `evidence`
 * object on this record, which this type deliberately omits — it is never
 * rendered by production code (see redactReceipt in receipts.ts).
 */
export type ExecutionReceipt = FreeformRecord & {
  id: string;
  run_id?: string;
  run_node_id?: string;
  attempt_id?: string;
  actor_system?: string;
  actor_id?: string;
  outcome?: string;
  summary?: string;
  evidence_sha256?: string;
  validation_status?: string;
  created_at?: string;
};

export type ExecutionFinding = FreeformRecord & {
  id: string;
  run_id?: string;
  severity?: string;
  code?: string;
  message?: string;
  entity_id?: string | null;
  created_at?: string;
};

export type ExecutionEvent = FreeformRecord & {
  id: string;
  run_id?: string;
  event_type?: string;
  payload?: FreeformRecord;
  created_at?: string;
};

export type ExecutionRunDetail = FreeformRecord & {
  id: string;
  project_id?: string;
  plan_version_id?: string;
  export_id?: string | null;
  runtime_system?: string;
  runtime_run_id?: string;
  status?: string;
  input_fingerprint?: string;
  created_at?: string;
  started_at?: string;
  completed_at?: string | null;
  verified_at?: string | null;
  nodes?: ExecutionRunNode[];
  attempts?: ExecutionAttempt[];
  receipts?: ExecutionReceipt[];
  findings?: ExecutionFinding[];
  events?: ExecutionEvent[];
  ready_nodes?: FreeformRecord[];
};

export type ExecutionRunSummary = FreeformRecord & {
  id: string;
  runtime_system?: string;
  runtime_run_id?: string;
  status?: string;
  created_at?: string;
  verified_at?: string | null;
};

export type PageResult<T> = ApiResult<{ items: T[] }>;
export type OperationResult = ApiResult<{ operation: OperationLike & FreeformRecord }>;
