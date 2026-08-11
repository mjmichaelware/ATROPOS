import type { ApiResult } from "@/lib/api/client";
import type { OperationLike } from "@/lib/api/operations";
import type { AuthorityRelation, FreeformRecord, PlanDetail, PlanningWorkspace, PlanSummary } from "@/lib/graph/schemas";

export type { AuthorityRelation, FreeformRecord, PlanDetail, PlanningWorkspace, PlanSummary };

export const RELATION_TYPES = ["REQUIRES", "REFINES", "CONFLICTS_WITH", "DUPLICATES", "IMPLEMENTS", "VERIFIES", "RELATES_TO"] as const;
export type RelationType = (typeof RELATION_TYPES)[number];

export type PlanStatus = "DRAFT" | "BLOCKED" | "INVALID" | "VERIFIED" | "UNKNOWN";

export type FindingSeverity = "ERROR" | "WARNING" | "INFO" | "UNKNOWN";

export type PlanFinding = FreeformRecord & {
  id: string;
  plan_version_id?: string;
  severity?: string;
  code?: string;
  message?: string;
  entity_id?: string | null;
  created_at?: string;
};

export type PlanBinding = FreeformRecord & {
  id?: string;
  plan_version_id?: string;
  graph_node_id: string;
  atom_id: string;
  stage?: string;
  sequence_number?: number;
  canonical_statement?: string;
  kind?: string;
  modality?: string;
  created_at?: string;
};

export type RelationInput = {
  from_atom_id: string;
  to_atom_id: string;
  relation_type: RelationType;
  rationale?: string;
  confidence?: number;
};

export type RelationCreateResponse = AuthorityRelation;

export type PlanSynthesizeResponse = { operation: OperationLike & FreeformRecord };

export type PlanVerifyResponse = { operation: OperationLike & FreeformRecord };

export type PageResult<T> = ApiResult<{ items: T[] }>;
