import type { ApiResult } from "@/lib/api/client";
import type { OperationLike } from "@/lib/api/operations";

export type FreeformRecord = Record<string, unknown>;

export type ResearchCounts = FreeformRecord & {
  atoms?: number;
  dimensions?: number;
  open_dimensions?: number;
  resolved_dimensions?: number;
  not_applicable_dimensions?: number;
  ready_atoms?: number;
  tasks?: number;
  pending_tasks?: number;
  completed_tasks?: number;
  failed_tasks?: number;
  evidence?: number;
};

export type ResearchWorkspace = FreeformRecord & {
  project?: FreeformRecord;
  counts?: ResearchCounts;
  gap_matrix?: GapMatrix;
  tasks?: ResearchTask[];
  tasks_count?: number;
  tasks_has_more?: boolean;
};

export type DimensionStatusValue = "OPEN" | "RESOLVED" | "NOT_APPLICABLE" | "UNKNOWN";

export type GapCell = FreeformRecord & {
  atom_id?: string;
  dimension?: string;
  dimension_id?: string;
  status?: string;
  rationale?: string | null;
  task_id?: string | null;
};

export type GapAtom = FreeformRecord & {
  id: string;
  text?: string;
  label?: string;
  canonical_statement?: string;
  kind?: string;
  modality?: string;
  source_document_id?: string;
  document_id?: string;
  line_start?: number;
  line_end?: number;
  dimensions?: GapCell[] | Record<string, GapCell | string>;
};

export type GapMatrix = FreeformRecord & {
  summary?: ResearchCounts;
  atoms?: GapAtom[];
  atoms_count?: number;
  atoms_has_more?: boolean;
  dimensions?: string[];
};

export type ResearchTask = FreeformRecord & {
  id: string;
  project_id?: string;
  atom_id?: string;
  dimension?: string;
  status?: string;
  canonical_statement?: string;
  kind?: string;
  modality?: string;
  worker_id?: string | null;
  lease_expires_at?: string | null;
  attempt_count?: number;
  evidence?: EvidenceRecord[];
  conclusion?: string | null;
  applicability?: string | null;
};

export type EvidenceRecord = FreeformRecord & {
  id?: string;
  source_uri?: string;
  source_title?: string;
  excerpt?: string;
  publisher?: string;
  evidence_type?: string;
  reliability?: number;
  created_at?: string;
};

export type EvidenceInput = {
  source_uri: string;
  source_title: string;
  excerpt: string;
  publisher?: string;
  evidence_type?: string;
  reliability?: number;
};

export type ConclusionInput = {
  conclusion: string;
  applicability: "APPLICABLE" | "NOT_APPLICABLE";
  confidence: number;
  evidence_ids: string[];
};

export type OperationResponse = {
  operation: OperationLike & FreeformRecord;
};

export type PageResult<T> = ApiResult<{ items: T[] }>;
